<template lang="pug">
.permissions-dialog(ref="dialog")
  NioIconFramer(icon-name="display-lightbulb")
  h3.nio-h3.text-primary-darker Required Permissions
  p.nio-p.text-primary-dark
    span The connector requires the following permissions:
    ul
      li
        span.text-primary-darker ads_management:#{' '}
        span.text-primary-dark
          | used to list your ad accounts for display as well as create custom audiences and deliver audience data.
      li
        span.text-primary-darker business_management:#{' '}
        span.text-primary-dark
          | used to list the business associated with an ad account and determine whether an ad account can use custom
          | audiences.
      li
        span.text-primary-darker public_profile:#{' '}
        span.text-primary-dark
          | used to gather public information about the account being connected so that we can show which account
          | authorized the connector.
  .actions
    NioButton(
      normal-secondary
      @click="close"
    ) Close
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

.permissions-dialog
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