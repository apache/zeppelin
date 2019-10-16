<template>
  <div
    id="connection-status"
    class="status-bar-widget"
  >
    <a-tooltip placement="bottom">
      <template slot="title">
        <span>Server Connectivity</span>
      </template>
      <div
        class="ConnectionIndicator"
        :class="'ConnectionIndicator--' + connectivityStatus"
      >
        <div class="Status">
          <div class="Status__circle Status__circle--static"></div>
          <div class="Status__circle Status__circle--animated Status__circle--pulse"></div>
        </div>
        <div
          class="status-label"
        >
          {{ connectivityStatus.replace('trying', 'trying to connect') }}
        </div>
      </div>
    </a-tooltip>
  </div>
</template>

<script>
export default {
  name: 'Connectivity',
  computed: {
    connectivityStatus () {
      return this.$store.state.webSocketStatus
    }
  }
}
</script>

<style lang="scss" scoped>
</style>
