<template lang="pug">
v-app(ref="app")
  v-main
    router-view
</template>

<script>
export default {
  name: 'App',
  mounted() {
    this.nioInitializeApplication(this)
  }
}
</script>

<style lang="sass">
@import "@narrative.io/tackle-box/src/styles/global/_fonts"
@import "@narrative.io/tackle-box/src/styles/global/_colors"
@import "@narrative.io/tackle-box/src/styles/global/_color-helpers"
@import "@narrative.io/tackle-box/src/styles/global/_typography"
@import "@narrative.io/tackle-box/src/styles/global/_breakpoints"

// global
.v-overlay.v-overlay--active.theme--dark .v-overlay__scrim
  background-color: $c-canvas-darker !important
  opacity: 0.9 !important
  backdrop-filter: blur(0.1875rem)
  box-shadow: none

.app-header
  display: flex
  justify-content: center
  align-items: flex-start

.app-dialog
  background-color: $c-white
  max-height: unset !important
  width: calc(100vw - 4rem)
  padding: 1.5rem
  border: 0.0625rem solid $c-primary-lighter !important
  box-shadow: 0rem 0.0625rem 0.1875rem rgba(0, 0, 0, 0.1) !important
  border-radius: 0.75rem !important

.app-loading
  width: 100%
  height: 100%
  position: relative
  .v-progress-circular
    position: fixed
    left: 50%
    top: 18.75rem
    margin-left: -2.5rem
    z-index: 2

.step-loading
  width: 100%
  height: 100%
  position: relative
  .v-progress-circular
    position: absolute
    left: 50%
    top: -15px
    margin-left: -2.5rem
    z-index: 2

#app
  font-family: 'Euclid Circular A'
</style>
