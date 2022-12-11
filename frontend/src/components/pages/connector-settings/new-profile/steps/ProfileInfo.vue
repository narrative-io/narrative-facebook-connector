<template lang="pug">
.profile-info
  NioDestinationConnectorSettings(v-model="model")
</template>

<script>

export default {
  props: {
    destination: { type: Object, required: false },
    info: { type: Object, required: false }
  },
  data: () => ({
    model: {
      name: null,
      description: null
    }
  }),
  computed: {
    modelValid() {
      return this.model && this.model.name && this.model.description
    }
  },
  watch: {
    model: {
      deep: true,
      handler() {
        if (this.modelValid) {
          this.$emit('stepPayloadChanged', this.model)
        }
      }
    },
    info() {
      this.model = this.info
    }
  },
  mounted() {
    if (this.info) {
      this.model = this.info
    } else if (this.destination && !this.model.name) {
      this.model.name = this.destination.adAccount.name
    }
  }
};

</script>

<style lang="sass" scoped>

@import "@narrative.io/tackle-box/src/styles/global/_colors"
@import "@narrative.io/tackle-box/src/styles/"

</style>
