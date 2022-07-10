<template lang="pug">
.connect-account
  .step-loading(v-if="loading")
    v-progress-circular.progress(
      size="80"
      color="#1438F5"
      indeterminate
    )
  NioAlert(
    warning
    @dismiss="showInvalidTokenWarning = false"
    message="The Facebook connector requires that you grant all of the requested permissions. Please login again."
    :visible="!loading && showInvalidTokenWarning"
  )
  .filter(v-if="!modelValid(model) && !loading")
    .title-description
      .filter-title.nio-h4.text-primary-darker Connect Facebook Account
      .description.nio-p.text-primary-dark
        span
          | Log in to to authorize the conntor to seamlessly deliver your data purchases straight to a Facebook a custom
          | audience in one of your ad accounts.#{' '}
        p
          a.text-decoration-underline(@click="showPermissionsDialog = true") Learn more
          span #{' '}about the permissions required by the app.
    .filter-value.facebook-login-button.fill-width
      v-facebook-login(
        :app-id="appId"
        @login="onFacebookLogin"
        :login-options="facebookLoginOptions"
        v-model="facebookModel"
        @sdk-init="facebookSdkInit"
      )
        template(v-slot:login)
          span Login With Facebook
  .filter(v-if="modelValid(model)")
    .title-description
      .filter-title.nio-h4.text-primary-darker User
      .description.nio-p.text-primary-dark The Facebook user associated with the profile.
    .filter-value
      nio-text-field.fill-width(v-model="model.token.user.name" label="User" disabled)
  .filter(v-if="modelValid(model)")
    .title-description
      .filter-title.nio-h4.text-primary-darker Permissions
      .description.nio-p.text-primary-dark The set of permissions granted to the Narrative Facebook Connector.
    .filter-value
      nio-tags-field.fill-width(v-model="model.token.scopes" label="Permissions" disabled)
  NioDialog(
    v-model="showPermissionsDialog"
  )
    PermissionsDialog(
      @close="showPermissionsDialog = false"
    )
</template>

<script>
import { baseUrl, getHeaders } from '@/utils/serviceLayer'
import axios from 'axios'
import PermissionsDialog from "./PermissionsDialog"
import VFacebookLogin from 'vue-facebook-login-component'

export default {
  components: { PermissionsDialog, VFacebookLogin },
  props: {
    account: { type: Object, required: false }
  },
  data: () => ({
    // Narrative Audience Uploader Facebook app ID. Not a secret.
    appId: "554425321962851",
    // Facebook SDK, initialzed via callback.
    facebook: null,
    facebookLoginOptions: {
      // Request permissions required by the Facebook Connector in order to
      // manage the user's ad accounts.
      scope: "ads_management,business_management",
      // Populate scopes the user accepted in the Facebook auth response.
      return_scopes: true
    },
    // Model populated on login by Facebook SDK
    facebookModel: null,
    // The underlying scope component object
    facebookScope: null,
    // The last access token returned by the Facebook login SDK.
    // Used to debounce onFacebookLogin calls.
    mostRecentAccessToken: null,
    loading: false,
    // Connected account information
    model: null,
    showInvalidTokenWarning: false,
    showPermissionsDialog: false
  }),
  methods: {
    // NB: not a computed property as we rely on being able to read an up to date value outside the template.
    modelValid(model) {
      return model &&
        model.token.is_valid &&
        this.missingScopes(this.model.token.scopes).length === 0
    },
    facebookSdkInit({ FB, scope }) {
      this.facebook = FB
      this.facebookScope = scope
    },
    missingScopes(scopes) {
      return [
        'ads_management',
        'business_management'
      ].filter(scope => !scopes.includes(scope))
    },
    onFacebookLogin() {
      const authResponse = this.facebook.getAuthResponse()
      // Only recheck token validity if it's changed since the last time we checked.
      if (authResponse && authResponse.accessToken != this.mostRecentAccessToken) {
        this.mostRecentAccessToken = authResponse.accessToken
        this.loading = true
        axios.post(`${baseUrl}/tokens/metadata`, { "token": authResponse.accessToken }, getHeaders())
          .then(resp => {
            // Embed access token value inside the model.token.value for convenience
            this.model = {
              ...resp.data,
              token: {
                ...resp.data.token,
                value: authResponse.accessToken
              }
            }
            this.loading = false
            this.showInvalidTokenWarning = !this.modelValid(this.model)
            this.$emit('stepPayloadChanged', this.model)
          })
        }
    }
  },
  watch: {
    account() {
      this.model = this.account
    }
  },
  mounted() {
    if (this.account) {
      this.model = this.account
    }
  }
};
</script>

<style lang="sass" scoped>
@import "@narrative.io/tackle-box/src/styles/mixins/filter/_filter-group"
@import "@narrative.io/tackle-box/src/styles/mixins/filter/_filter-header"
@import "@narrative.io/tackle-box/src/styles/global/_colors"

.connect-account
  .filter
    +nio-filter-header
    .title-description
      padding-right: 2rem
    .filter-value
      .nio-text-field
        width: 100%
    .facebook-login-button
      align-items: flex-end
  & > .filter + .filter
    border-top: 0.0625rem solid $c-primary-lighter
  .fill-width
    width: 100%
</style>