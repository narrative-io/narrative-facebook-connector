<template lang="pug">
  .error-dialog(ref="dialog")
    NioIconFramer(
      icon-name="display-warning"
    )
    h3.nio-h3.text-primary-darker Something went wrong...
    p.nio-p.text-primary-dark If the problem persists, please contact a memeber of the Narrative support team.
    .actions
      NioButton(
        normal-secondary
        @click="close"
      ) Back
</template>

<script>

export default {
  methods: {
    close() {
      this.$emit('close')
    }
  }
};
</script>

<style lang="sass" scoped>

@import "@narrative.io/tackle-box/src/styles/global/_colors"

.error-dialog
  padding: 6.25rem 1.5rem 4.875rem 1.5rem
  background-color: $c-white
  border: 0.0625rem solid $c-primary-lighter
  border-radius: 0.75rem
  display: flex
  flex-direction: column
  align-items: center
  .nio-icon-framer
    margin-bottom: 1rem
  h3
    margin-bottom: 0.5rem
  p
    margin-bottom: 2.5rem
  .actions
    display: flex
    justify-content: center
    align-items: center
    & > * + *
      margin-left: 1rem
</style>