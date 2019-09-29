<template>
  <section
    v-show="tabContent.path === (currentActiveTab && currentActiveTab.path)"
    class="tab-content w100 h100"
    :id="computedId"
    role="tabpanel"
  >
    <slot />
  </section>
</template>

<script>
export default {
  name: 'tab',
  props: {
    id: { default: null },
    name: { required: true },
    tabContent: { required: true }
  },
  computed: {
    computedId () {
      return this.id ? this.id : this.name.toLowerCase().replace(/ /g, '-')
    },
    hash () {
      return '#' + this.computedId
    },
    currentActiveTab () {
      return this.$store.state.TabManagerStore.currentTab
    }
  }
}
</script>
