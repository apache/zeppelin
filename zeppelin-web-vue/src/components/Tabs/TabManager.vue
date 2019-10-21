<template>
  <div class="w100 h100">
    <ul role="tablist" class="tabs-list">
      <li
        v-for="(tab, i) in tabs"
        :key="i"
        :class="{ 'active': (tab.id === (currentActiveTab && currentActiveTab.id)) }"
        class="tab"
        role="presentation"
      >
        <a
          :aria-controls="tab.hash"
          :aria-selected="tab.isActive"
          v-bind:title="tab.name"
          v-on:click="setActiveTab(tab, $event)"
          :href="tab.hash"
          class="tab-title"
          role="tab"
        >
          {{ tab.isActive }}
          <!-- Pending Tab Icon -->
          <span>{{ tab.name }}</span>
        </a>

        <a
          href="javascript: void(0);"
          class="tab-close"
          @click="closeTab(tab)"
          title="Close"
        >
          <!-- <a-icon type="close" size="small" /> -->
          <i class="custom-icon icon-close" />
        </a>
      </li>
    </ul>

    <div class="tabs-content scrollbar">
      <slot />
    </div>
  </div>
</template>

<script>
import wsFactory from '@/services/ws-factory'

export default {
  name: 'tabManager',
  data: () => ({
    activeTabIndex: 0,
    lastActiveTabHash: ''
  }),
  computed: {
    tabs () {
      return this.$store.state.TabManagerStore.tabs
    },
    currentActiveTab () {
      return this.$store.state.TabManagerStore.currentTab
    }
  },
  methods: {
    setActiveTab (tab) {
      this.$store.dispatch('setActiveTab', tab)
    },
    closeTab (tab) {
      if (tab.type === 'note') {
        wsFactory.closeConn(tab.id)
      }

      this.$store.dispatch('removeTab', tab)
    }
  }
}
</script>

<style lang="scss" scoped>
.icon-close {
  background: url("data:image/svg+xml,%3Csvg fill='%23616161' height='16' viewBox='0 0 24 24' width='16' xmlns='http://www.w3.org/2000/svg'%3E%3Cpath d='M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z'/%3E%3Cpath d='M0 0h24v24H0z' fill='none'/%3E%3C/svg%3E") no-repeat;
  height: 20px;
  width: 20px;
  display: block;
  margin: 4px;
}
</style>
