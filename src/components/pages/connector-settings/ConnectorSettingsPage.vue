<template lang="pug">
.connector-settings-page
  .app-loading(v-if="loading")
    v-progress-circular.progress(
      size="80"
      color="#1438F5"
      indeterminate
    )
  NioTabs(
    v-else
    v-model="activeTab"
    :tabs="tabs"
  )
    template(
      v-slot:listProfiles
    )
      .header.app-header
        h1.nio-h1.text-primary-darker Facebook Profiles
        .header-buttons
          NioButton(
            v-if="profiles && profiles.length > 0"
            caution-outlined
            @click="disconnectAccount"
          ) Disconnect Account
          NioButton.ml-2(
            icon-name="utility-plus"
            normal-primary-prepend
            @click="changeTab('newProfile')"
          ) New Profile
      .no-profiles(v-if="profiles && profiles.length === 0")
        NioIconFramer(
          icon-name="display-curvy-arrow"
        )
        h3.nio-h3.text-primary-darker You haven't created a profile
        p.nio-p.text-primary-dark You need to have at least one profile to use this connector.
        NioButton(
          icon-name="utility-chevron-right"
          normal-tertiary-append
          @click="learnMore"
        ) Learn more about creating Facebook profiles
      .profiles(v-else-if="profiles")
        ProfileList(
          :profiles="profiles"
          @deleteProfile="deleteProfile($event)"
        )
    template(
      v-slot:newProfile
    )
      .header.new-profile.app-header
        NioButton(
          icon-name="utility-chevron-left"
          normal-tertiary-prepend
          @click="changeTab('listProfiles')"
        ) Back to main
        h1.nio-h1.text-primary-darker New Profile
      NewProfile(
        :key="newProfileElementId"
        @profileSaved="profileSaved"
      )
  NioDialog(
    v-model="confirmDeleteDialog"
  )
    ConfirmDeleteDialog(
      @cancel="confirmDeleteDialog = false"
      @confirm="confirmDeleteProfile"
    )
  NioDialog(
    v-model="confirmDisconnectDialog"
  )
    ConfirmDisconnectDialog(
      @cancel="confirmDisconnectDialog = false"
      @confirm="confirmDisconnectAccount"
    )
  NioDialog(
    v-model="errorDialog"
  )
    ErrorDialog(
      @close="errorDialog = false"
    )
  //- Instantiate invisble login button so that the user can be logged out if they choose to disconnect their account.
  VFacebookLogin.d-none(
    :app-id="appId"
    @sdk-init="facebookSdkInit"
  )
</template>

<script>

import axios from 'axios'
import ConfirmDeleteDialog from './ConfirmDeleteDialog'
import ConfirmDisconnectDialog from './ConfirmDisconnectDialog'
import ErrorDialog from './ErrorDialog'
import NewProfile from './new-profile/NewProfile'
import ProfileList from './list-profiles/ProfileList'
import VFacebookLogin from 'vue-facebook-login-component'
import { makeRandomId } from '@narrative.io/tackle-box/src/modules/helpers'
import { NioOpenApiModule } from '@narrative.io/tackle-box'
import { baseUrl, setHeaders, getHeaders } from '@/utils/serviceLayer'

export default {
  components: {
    ConfirmDeleteDialog,
    ConfirmDisconnectDialog,
    ErrorDialog,
    NewProfile,
    ProfileList,
    VFacebookLogin
  },
  data: () => ({
    activeTab: 0,
    // Narrative Audience Uploader Facebook app ID. Not a secret.
    appId: "554425321962851",
    confirmDeleteDialog: false,
    confirmDisconnectDialog: false,
    errorDialog: false,
    // The underlying scope component object of the Facebook SDK
    facebookScope: null,
    loading: true,
    newProfileElementId: null,
    profiles: null,
    profileToDelete: null,
    tabs: [
      {
        name: 'listProfiles',
        label: ''
      },
      {
        name: 'newProfile',
        label: ''
      }
    ]
  }),
  mounted() {
    this.newProfileElementId = makeRandomId()
    NioOpenApiModule.initCallback(this.openApiInit)
  },
  methods: {
    openApiInit(token) {
      setHeaders(token)
      this.getProfiles()
    },
    changeTab(newTabName) {
      this.activeTab = this.tabs.indexOf(this.tabs.find(tab => tab.name === newTabName))
    },
    confirmDeleteProfile() {
      parent.postMessage(
        {
        name: 'scrollTo',
        payload: {
          x: 0,
          y: 0
        }
        },
        "*"
      )
      this.confirmDeleteDialog = false
      this.loading = true
      axios.post(`${baseUrl}/profiles/${this.profileToDelete.id}/archive`, null, getHeaders())
        .then(
          () => this.getProfiles(),
          () => {
            this.errorDialog = true
            this.loading = false
          }
      )
    },
    confirmDisconnectAccount() {
      parent.postMessage(
        {
        name: 'scrollTo',
        payload: {
          x: 0,
          y: 0
        }
        },
        "*"
      )
      this.confirmDisconnectDialog = false
      this.loading = true
      axios.post(`${baseUrl}/profiles/disconnect`, null, getHeaders())
        .then(
          () => {
            this.facebookScope.logout()
            return this.getProfiles()
          },
          () => {
            this.errorDialog = true
            this.loading = false
          }
        )
    },
    deleteProfile(profile) {
      this.profileToDelete = profile
      this.confirmDeleteDialog = true
    },
    disconnectAccount() {
      this.confirmDisconnectDialog = true
    },
    getProfiles() {
      this.profiles = []
      axios.get(`${baseUrl}/profiles`, getHeaders()).then(resp =>
        {
          this.loading = false
          this.profiles = resp.data.records.filter(profile => profile.status === 'enabled')
          this.changeTab('listProfiles')
        },
        () => {
          this.errorDialog = true
          this.loading = false
        }
      )
    },
    facebookSdkInit({ scope }) {
      this.facebookScope = scope
    },
    profileSaved() {
      this.newProfileElementId = makeRandomId()
      this.activeTab = 0
      this.loading = true
      this.getProfiles()
    },
    learnMore() {
      // todo
    }
  }
};
</script>

<style lang="sass" scoped>

@import "@narrative.io/tackle-box/src/styles/global/_colors"

.connector-settings-page
  .header-buttons
    display: flex

  .nio-tabs
    ::v-deep .v-tabs
      display: none
    ::v-deep .nio-divider
      display: none
  ::v-deep .v-tabs-items
    display: flex
    flex-direction: column
    background-color: $c-white
    padding: 2rem
    .header
      display: flex
      justify-content: space-between
      align-items: flex-start
      position: relative
      margin-bottom: 2rem
    .no-profiles
      padding: 9.6875rem 1.5rem 11.1875rem 1.5rem
      background-color: $c-canvas
      border: 0.0625rem solid $c-primary-lighter
      border-radius: 0.75rem
      display: flex
      flex-direction: column
      align-items: center
      .nio-icon-framer
        margin-bottom: 1.25rem
      h3
        margin-bottom: 0.625rem
      p
        margin-bottom: 1.75rem
    .save-action
      display: flex
      justify-content: flex-end
      align-items: center
      margin: 1.5rem
</style>