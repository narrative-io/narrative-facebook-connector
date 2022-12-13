<template lang="pug">
  .new-profile
    .app-loading(v-if="loading")
      v-progress-circular.progress(size="80" indeterminate color="#1438F5")
    NioStepper(
      v-else
      :ordered-steps="steps"
      :current-step="currentStep"
      :completed-steps="completedSteps"
      final-step-label="Save and Finish"
      @nextStep="nextStep"
      @previousStep="previousStep"
      @submit="saveProfile"
      @stepSelected="stepSelected($event)"
    )
      NioStep(
        v-if="steps.includes('Connect Account')"
        :valid="accountValid"
        step-name="Connect Account"
      )
        template(v-slot:content)
          NioDivider(horizontal-solo)
          ConnectAccountStep(
            :account="stepPayloads['Connect Account']"
            :current-step="currentStep"
            @stepPayloadChanged="updatePayload('Connect Account', $event)"
            @setStepIncomplete="setStepIncomplete('Connect Account')"
          )
      NioStep(
        v-if="steps.includes('Choose Destination')"
        :valid="destinationValid"
        step-name="Choose Destination"
      )
        template(v-slot:content)
          NioDivider(horizontal-solo)
          ChooseDestinationStep(
            :account="stepPayloads['Connect Account']"
            :current-step="currentStep"
            :destination="stepPayloads['Choose Destination']"
            @stepPayloadChanged="updatePayload('Choose Destination', $event)"
            @setStepIncomplete="setStepIncomplete('Choose Destination')"
          )
      NioStep(
        v-if="steps.includes('Profile Info')"
        :valid="infoValid"
        step-name="Profile Info"
      )
        template(v-slot:content)
          NioDivider(horizontal-solo)
          ProfileInfoStep(
            :current-step="currentStep"
            :destination="stepPayloads['Choose Destination']"
            @stepPayloadChanged="updatePayload('Profile Info', $event)"
            @setStepIncomplete="setStepIncomplete('Profile Info')"
          )
</template>

<script>

import { NioOpenApiModule } from '@narrative.io/tackle-box'
import { baseUrl, getHeaders } from '@/utils/serviceLayer'
import axios from 'axios'
import ChooseDestinationStep from './steps/ChooseDestination'
import ConnectAccountStep from './steps/ConnectAccount'
import ProfileInfoStep from './steps/ProfileInfo'

export default {
  components: { ChooseDestinationStep, ConnectAccountStep, ProfileInfoStep },
  data: () => ({
    completedSteps: [],
    currentStep: 'Connect Account',
    errorDialog: false,
    loading: false,
    steps: [ 'Connect Account', 'Choose Destination', 'Profile Info' ],
    stepPayloads: {
      'Connect Account': null,
      'Choose Destination': null,
      'Profile Info': null
    }
  }),
  computed: {
    accountValid() {
      const account = this.stepPayloads['Connect Account']
      return account &&
        account.token.is_valid &&
        account.token.scopes.includes("ads_management") &&
        account.token.scopes.includes("business_management")
    },
    destinationValid() {
      const destination = this.stepPayloads['Choose Destination']
      return destination && destination.adAccount.supports_custom_audiences
    },
    infoValid() {
      const info = this.stepPayloads['Profile Info']
      return Boolean(info && info.name && info.description)
    }
  },
  mounted() {
    NioOpenApiModule.initCallback(this.openApiInit)
  },
  methods: {
    saveProfile() {
      this.loading = true
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
      const account = this.stepPayloads['Connect Account']
      const destination = this.stepPayloads['Choose Destination']
      const info = this.stepPayloads['Profile Info']
      const createReq = {
        ad_account_id: destination.adAccount.id,
        description: info.description,
        name: info.name,
        token: account.token.value
      }
      axios.post(`${baseUrl}/profiles`, createReq, getHeaders())
        .then(resp => axios.post(`${baseUrl}/profiles/${resp.data.id}/enable`, null, getHeaders()))
        .then(
          () => {
            this.stepPayloads = {
              'Connect Account': null,
              'Choose Destination': null,
              'Profile Info': null
            },
            this.loading = false
            this.completedSteps = []
            this.currentStep = 'Connect Account'
            this.$emit('profileSaved')
          },
          () => {
            this.errorDialog = true
            this.loading = false
          }
        ).catch(() => {
          this.errorDialog = true
          this.loading = false
        })
    },
    nextStep() {
      if (!this.completedSteps.includes(this.currentStep)) {
        this.completedSteps.push(this.currentStep)
      }

      this.currentStep = this.steps[this.steps.indexOf(this.currentStep) + 1]
      this.scrollToStep(this.steps.indexOf(this.currentStep))
    },
    previousStep() {
      this.currentStep = this.steps[this.steps.indexOf(this.currentStep) - 1]
      this.scrollToStep(this.steps.indexOf(this.currentStep))
    },
    stepSelected(stepName) {
      this.currentStep = stepName
    },
    updatePayload(step, payload) {
      this.stepPayloads[step] = payload
    },
    scrollToStep(stepIndex) {
      this.$nextTick(() => {
        const top = 35 + stepIndex * 130
        parent.postMessage(
          {
            name: 'scrollTo',
            payload: {
              x: 0,
              y: top
            }
          },
          "*"
        )
      })
    }
  }
}
</script>

<style lang="sass" scoped>

@import "@narrative.io/tackle-box/src/styles/global/_colors"

.new-profile
  ::v-deep .nio-step-header-slat .nio-slat-action
    justify-content: flex-end
</style>