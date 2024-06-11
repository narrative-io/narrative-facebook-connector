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
          | Log in to to authorize the connector to seamlessly deliver your data purchases straight to a Facebook custom
          | audience in one of your ad accounts.#{' '}
        p
          a.text-decoration-underline(@click="showPermissionsDialog = true") Learn more
          span #{' '}about the permissions required by the app.
    .filter-value.facebook-login-button.fill-width
      button(@click="logInWithFacebook") Login with Facebook
  .filter(v-if="modelValid(model)")
    .title-description
      .filter-title.nio-h4.text-primary-darker User
      .description.nio-p.text-primary-dark The Facebook user associated with the profile.
    .filter-value
      NioTextField.fill-width(v-model="model.token.user.name" label="User" disabled)
  .filter(v-if="modelValid(model)")
    .title-description
      .filter-title.nio-h4.text-primary-darker Permissions
      .description.nio-p.text-primary-dark The set of permissions granted to the Narrative Facebook Connector.
    .filter-value
      NioTagsField.fill-width(v-model="model.token.scopes" label="Permissions" disabled)
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

export default {
  components: { PermissionsDialog },
  props: {
    account: { type: Object, required: false }
  },
  data: () => ({
    appId: "554425321962851",
    facebook: null,
    facebookLoginOptions: {
      scope: "ads_management,business_management",
      return_scopes: true
    },
    facebookModel: null,
    facebookScope: null,
    mostRecentAccessToken: null,
    loading: false,
    model: null,
    showInvalidTokenWarning: false,
    showPermissionsDialog: false
  }),
  mounted() {
    if (this.account) {
      this.model = this.account
    }
    this.init()
  },
  methods: {
    async init() {
      await this.loadFacebookSDK(document, "script", "facebook-jssdk");
    },
    async logInWithFacebook() {
      if (!window.FB) {
        await this.loadFacebookSDK(document, "script", "facebook-jssdk");
      }
      
      window.FB.init({
        appId: this.appId,
        version: "v20.0"
      });

      window.FB.Event.subscribe('auth.login', function(response) {
        console.log("auth.login", response)
      });
      window.FB.Event.subscribe('xfbml.render', function() {
        console.log("xfbml.render")
      });
      window.FB.Event.subscribe('auth.authResponseChange', function(response) {
        console.log("auth.authResponseChange", response)
      });
      window.FB.Event.subscribe('auth.statusChange', function(response) {
        console.log("auth.statusChange", response)
      });

      // wait for 1 sec
      await new Promise(resolve => setTimeout(resolve, 1000))

      console.log("logging in")
      console.log("window.FB.login ", window.FB.login)
      window.FB.login(response => {
        console.log("response", response)
        if (response.authResponse) {
          this.onFacebookLogin(response.authResponse);
        } else {
          alert("User cancelled login or did not fully authorize.");
        }
      }, this.facebookLoginOptions);
    },
    async loadFacebookSDK(d, s, id) {
      return new Promise((resolve, reject) => {
        let js, fjs = d.getElementsByTagName(s)[0];
        if (d.getElementById(id)) {
          return resolve();
        }
        js = d.createElement(s);
        js.id = id;
        js.src = "https://connect.facebook.net/en_US/sdk.js";
        js.onload = resolve;
        js.onerror = reject;
        fjs.parentNode.insertBefore(js, fjs);
      });
    },
    onFacebookLogin(authResponse) {
      console.log("authResponse", authResponse)
      if (authResponse && authResponse.accessToken != this.mostRecentAccessToken) {
        this.mostRecentAccessToken = authResponse.accessToken
        this.loading = true
        axios.post(`${baseUrl}/tokens/metadata`, { "token": authResponse.accessToken }, getHeaders())
          .then(resp => {
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
          }, err => {
            console.error("token err", err)
            this.loading = false
          })
      }
    },
    modelValid(model) {
      return model &&
        model.token.is_valid &&
        this.missingScopes(this.model.token.scopes).length === 0
    },
    missingScopes(scopes) {
      return [
        'ads_management',
        'business_management'
      ].filter(scope => !scopes.includes(scope))
    }
  },
  watch: {
    model: {
      handler: function (model) {
        console.log("model changed", model)
      },
      deep: true
    },
    account() {
      console.log("account changed", this.account)
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
