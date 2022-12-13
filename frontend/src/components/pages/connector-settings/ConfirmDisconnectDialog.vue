<template lang="pug">
.confirm-disconnect-dialog(ref="dialog")
  NioIconFramer(
    icon-name="display-warning"
  )
  h3.nio-h3.text-primary-darker Are you sure you want to disconnect your Facebook account?
  p.nio-p.text-primary-dark All your profiles will be removed and the Narrative Audience Uploader will no longer have permission to manage your ad accounts.
  .actions
    NioButton(
      normal-secondary
      @click="cancel"
    ) Cancel
    NioButton(
      caution-filled-prepend
      @click="confirm"
    ) Disconnect Account
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

.confirm-disconnect-dialog
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