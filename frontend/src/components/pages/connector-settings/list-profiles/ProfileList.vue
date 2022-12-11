<template lang="pug">
.profile-list
  NioSlatTable(
    v-if="columns"
    :items="profiles"
    :columns="columns"
    :header-modules="['count']"
    action="expand"
  )
    template(v-slot:item-expanded="slotProps")
      .split-row
        .display-row.display-table
          .display-column
            .nio-h4.text-primary-darker Ad Account
            .nio-p.text-primary-dark {{ slotProps.item.ad_account.name }} ({{ slotProps.item.ad_account.id }})
        .display-row.display-table
          .display-column
            .nio-h4.text-primary-darker Business
            .nio-p.text-primary-dark.description {{ slotProps.item.ad_account.business.name }}
      .split-row
        .display-row.display-table(v-if="slotProps.item.token.is_valid")
          .display-column
            .nio-h4.text-primary-darker Connected User
            .nio-p.text-primary-dark.description {{ slotProps.item.token.user.name }}
        .display-row.display-table
          .display-column
            .nio-h4.text-primary-darker Connection Status
            .nio-p.text-primary-dark {{ slotProps.item.token.is_valid ? 'Enabled' : 'Token Expired' }}
      .subscription-footer
        .subscription-actions(v-if="slotProps.item.status !== 'archived'")
          NioButton(
            caution-text
            @click="deleteProfile(slotProps.item)"
          ) Delete Profile
</template>

<script>

import moment from 'moment'

export default {
  props: {
    "profiles": { type: Array, required: true }
  },
  data: () => ({
    columns: null
  }),
  mounted() {
    this.makeColumns()
  },
  methods: {
    makeColumns() {
      this.columns = [
        {
          name: "slat",
          props: {
            title: this.computeTitle,
            subtitle: this.computeSubtitle
          }
        },
        {
          name: "status",
          label: "Status",
          computed: this.computeStatus
        }
      ]
    },
    computeLastUpdated(item) {
      return this.formatTimestamp(item.updated_at)
    },
    computeStatus(item) {
      return item.token.is_valid ? "Enabled" : "Disabled"
    },
    computeSubtitle(item) {
      return item.description
    },
    computeTitle(item) {
      return item.name
    },
    deleteProfile(profile) {
      this.$emit('deleteProfile', profile)
    },
    formatTimestamp(date) {
      moment(date).format('MMM D, YYYY')
    }
  }
};
</script>

<style lang="sass" scoped>

@import "@narrative.io/tackle-box/src/styles/global/_colors"
@import "@narrative.io/tackle-box/src/styles/mixins/table/_slat-table-expanded-row"

.profile-list
  ::v-deep .expanded-row
    +nio-slat-table-expanded-row
  ::v-deep .v-data-table
    tr.expanded
      background-color: $c-canvas

</style>