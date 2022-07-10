<template lang="pug">
.nio-select-destination
  p.title-description
    .filter-title.nio-h4.text-primary-darker Ad Account
    .description.nio-p.text-primary-dark
      | Choose the ad account to which purchased data will be delivered.
      | You will be able to choose a specific audience at checkout using quick settings, or we'll create one a new one
      | to host your purchased data.
  NioExpansionPanels(
    :multiple="false"
  )
    NioExpansionPanel(
      v-for="adAccount in adAccounts"
      :key="adAccount.id"
    )
      template(v-slot:header)
        NioSwitch(
          @click.stop="selectAdAccount(adAccount)"
          :disabled="!adAccount.supports_custom_audiences"
          v-model="adAccount.selected"
        )
        div.destination-content
          h2.nio-h4.text-primary-darker {{ adAccount.name }}
          p
          p(v-if="!adAccount.supports_custom_audiences").text-primary-dark
            span
              NioIcon.pr-2(name="display-warning" size="16")
              span This ad account cannot be used with custom audiences. Click to learn more.
      template(v-slot:content)
        .control
          .title-description
            .filter-title.nio-h4.text-primary-darker Ad Account ID
          .filter-value
            NioTextField(v-model="adAccount.id" label="Ad Account ID" disabled)
        .control
          .title-description
            .filter-title.nio-h4.text-primary-darker Business
            .description.nio-p.text-primary-dark
              | The business associated with the ad account.
          .filter-value(v-if="adAccount.business")
            NioTextField(v-model="adAccount.business.name" label="Business" disabled)
          .filter-value(v-else).text-primary-dark
            span
              span Custom audiences can only be used with ad accounts associted with a business. This ad account is not associated with a business.
        .control
          .title-description
            .filter-title.nio-h4.text-primary-darker Terms of Service
            .description.nio-p.text-primary-dark
              | The business associated with the ad account.
          .filter-value(v-if="adAccount.user_accepted_custom_audience_tos").text-primary-dark
            span
              NioIcon.pr-2(name="utility-check-circle" color="#43B463" size="14")
              span.pl-2 Terms of Service accepted.
          .filter-value(v-else).text-primary-dark
            span Facebook's Custom Audience Terms of Service have not been accepted.
            a.go-fb(
              :href="`https://business.facebook.com/ads/manage/customaudiences/tos/?act=${removeAdAccountIdPrefix(adAccount.id)}`"
              target="_blank"
            )
              span #{' '}Accept Custom Audience Terms of Service on Facebook
              NioIcon.pl-2(name="utility-external-link" color="#1438F5")
</template>

<script>

export default {
  props: {
    // input account information
    account: { type: Object, required: true },
    // pre-selected destination ad account
    destination: { type: Object, required: false }
  },
  data: () => ({
    model: {
      adAccount: null
    }
  }),
  computed: {
    modelValid() {
      return this.model && this.model.adAccount && this.model.adAccount.supports_custom_audiences
    },
    adAccounts() {
      // ad accounts sorted such that ineligible ad accounts are displayed last
      return [...this.account.ad_accounts].sort((a, b) => {
        if (a.supports_custom_audiences && !b.supports_custom_audiences) {
          return -1;
        } else if (b.supports_custom_audiences && !a.supports_custom_audiences) {
          return 1;
        } else {
          return a.name.localeCompare(b.name)
        }
      })
    }
  },
  watch: {
    destination() {
      this.model = this.destination
      this.model.adAccount.selected = true
    },
    model: {
      deep: true,
      handler() {
        if (this.modelValid) {
          this.$emit('stepPayloadChanged', this.model)
        }
      }
    },
  },
  methods: {
    removeAdAccountIdPrefix(id) {
      return id.replace("act_", "")
    },
    selectAdAccount(selectedAdAccount) {
      this.model.adAccount = selectedAdAccount
      // ensure only a single ad account is selected
      this.adAccounts.map(adAccount => {
        if (adAccount.selected && adAccount.id != selectedAdAccount.id) {
          adAccount.selected = false
        }
    })
    }
  },
  mounted() {
    if (this.destination) {
      this.model = this.destination
    }
  }
};

</script>

<style lang="sass" scoped>

@import "@narrative.io/tackle-box/src/styles/mixins/_radio-button"
@import "@narrative.io/tackle-box/src/styles/mixins/_radio-group"
@import "@narrative.io/tackle-box/src/styles/mixins/connectors/destination/select-destination/_select-destination"

@import "@narrative.io/tackle-box/src/styles/global/_colors"
@import "@narrative.io/tackle-box/src/styles/"
</style>