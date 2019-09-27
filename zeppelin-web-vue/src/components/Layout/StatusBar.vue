<template>
  <div class="status-bar">
    <div class="status-left">
    </div>

    <div class="status-right">
      <div
        id="toggle-dark-mode"
        class="status-bar-widget"
      >
        <a-tooltip placement="top">
          <template slot="title">
            <span>Toggle Dark Mode</span>
          </template>
          <a-switch
            defaultChecked
            @change='toggleDarkMode'
            size="small"
          />
        </a-tooltip>
      </div>

      <div
        id="connection-status"
        class="status-bar-widget"
      >
        <a-tooltip placement="top">
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
    </div>
  </div>
</template>

<script>
export default {
  name: 'StatusBar',
  computed: {
    darkModeEnabled () {
      return this.$store.state.darkMode
    },
    connectivityStatus () {
      return this.$store.state.webSocketStatus
    }
  },
  methods: {
    toggleDarkMode () {
      if (this.darkModeEnabled) {
        this.$store.dispatch('toggleDarkMode', false)
      } else {
        this.$store.dispatch('toggleDarkMode', true)
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.status-bar {
  height: 24px;
  background: #ececec;
  position: relative;

  .status-left {
    display: flex;
    float: left;
    margin-left: 48px;
  }

  .status-right {
    display: flex;
    float: right;
  }
}

.status-bar-widget {
  .status-pane-label {
    margin-left: 10px;
  }

  &:hover {
    background-color: #c5c5c5;
  }

  a {
    padding: 0 8px;
  }

  button {
    margin: 0 8px;
  }
}

</style>
