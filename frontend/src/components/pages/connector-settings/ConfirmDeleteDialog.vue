<template lang="pug">
.confirm-delete-dialog(ref="dialog")
  NioIconFramer(
    icon-name="display-warning"
  )
  h3.nio-h3.text-primary-darker Are you sure you want to remove this profile?
  p.nio-p.text-primary-dark You’ll need to start from scratch and reconfigure the connection once you remove it.
  .actions
    NioButton(
      normal-secondary
      @click="cancel"
    ) Cancel
    NioButton(
      caution-filled-prepend
      @click="confirm"
    ) Remove Profile
</template>

<script>

export default {
  methods: {
    cancel() {
      this.$emit('cancel')
    },
    confirm() {
      this.$emit('confirm')
    }
  }
};
</script>

<style lang="sass" scoped>

@import "@narrative.io/tackle-box/src/styles/global/_colors"

.confirm-delete-dialog
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
    > *
      margin-left: 1rem
</style>