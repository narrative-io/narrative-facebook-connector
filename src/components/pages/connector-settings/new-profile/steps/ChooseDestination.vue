<template lang="pug">
.choose-destination
  .filter
    .title-description
      .filter-title.nio-h4.text-primary-darker Ad Account
      .description.nio-p.text-primary-dark
        | Choose the ad account to which purchased data will be delivered.
        | You will be able to choose a specific audience at checkout using quick settings, or we'll create one a new one
        | to host your purchased data.
        NioRadioGroup(v-model="model.adAccount")
          template(v-for="adAccount in sortedAdAccounts")
            NioRadioButton(:value="adAccount" :label="`${adAccount.name}`" :disabled="!adAccount.supports_custom_audiences")
</template>

<script>

export default {
  props: {
    account: { type: Object, required: true },
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
    sortedAdAccounts() {
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
  mounted() {
    if (this.destination) {
      this.model = this.destination
    }
  }
};

</script>

<style lang="sass" scoped>

@import "@narrative.io/tackle-box/src/styles/global/_colors"
@import "@narrative.io/tackle-box/src/styles/"

.choose-destination
</style>