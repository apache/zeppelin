<template>
  <div class="left-nav-bar">
    <ul class="tabs">
      <li>
        <a
          :class="{'active': this.$store.state.selectedLeftNavTab === 'folders'}"
          placement="right"
          href="javascript:void(0);"
          data-ref="folders"
          @click="selectTab('folders')"
        >
          <a-tooltip placement="right">
            <template slot="title">
              <span>Folders</span>
            </template>
            <a-icon type="folder" />
          </a-tooltip>
        </a>
      </li>

      <li>
        <a
          :class="{'active': this.$store.state.selectedLeftNavTab === 'activity'}"
          href="javascript:void(0);"
          data-ref="activity"
          @click="selectTab('activity')"
        >
          <a-tooltip placement="right">
            <template slot="title">
              <span>Activity</span>
            </template>
            <a-icon type="eye" />
          </a-tooltip>
        </a>
      </li>

      <li>
        <a
          :class="{'active': this.$store.state.selectedLeftNavTab === 'helium'}"
          href="javascript:void(0);"
          data-ref="helium"
          @click="selectTab('helium')"
        >
          <a-tooltip placement="right">
            <template slot="title">
              <span>Helium</span>
            </template>
            <a-icon type="experiment" />
          </a-tooltip>
        </a>
      </li>

      <li>
          <a
            :class="{'active': this.$store.state.selectedLeftNavTab === 'trash'}"
            href="javascript:void(0);"
            data-ref="trash"
            @click="selectTab('trash')"
          >
            <a-tooltip placement="right">
              <template slot="title">
                <span>Recycle Bin</span>
              </template>
              <a-icon type="delete" />
            </a-tooltip>
          </a>
        </li>
    </ul>

    <div id="nav-actions">
      <div class="divider" />
      <ul>
        <li>
          <a
            class=""
            href="javascript:void(0);"
            @click="togglePreferences()"
          >
            <a-tooltip placement="right">
              <template slot="title">
                <span>Preferences</span>
              </template>
              <a-icon type="setting" />
            </a-tooltip>
          </a>
        </li>
      </ul>
    </div>
  </div>
</template>

<script>
export default {
  name: 'LeftNavBar',
  computed: {
    showPrefererences () {
      return this.$store.state.showPrefererences
    }
  },
  methods: {
    selectTab (tab) {
      this.$store.dispatch('selectLeftNavTab', tab)
    },
    togglePreferences () {
      if (this.showPrefererences) {
        this.$store.dispatch('togglePreferences', false)
      } else {
        this.$store.dispatch('togglePreferences', true)
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.left-nav-bar {
  width: 48px;
  height: 100%;
  background: #f3f3f3;
  overflow: hidden;
  position: relative;

  ul {
    list-style: none;
    margin: 0;
    padding: 0;

    li {
      a {
        height: 48px;
        width: 48px;
        padding: 12px 13px 12px 11px;
        display: block;
        color: #333;
        border-left: 4px solid transparent;

        svg {
          height: 18px;
          width: 18px;
          color: #333333;
        }

        &.active {
          background: #FFF;
          border-left-color: #2f71a9;

          &:hover {
            background: #FFF;

            svg {
              color: #333333;
            }
          }
        }

        &:hover {
          svg {
            color: #2f71a9;
          }
        }

      }
    }
  }

  #nav-actions {
    position: absolute;
    bottom: 0;

    .divider {
      background-image: linear-gradient(45deg, #ffffff 25%, #888888 25%, #888888 50%, #ffffff 50%, #ffffff 75%, #888888 75%, #888888 100%);
      background-size: 5.66px 5.66px;
      height: 6px;
      display: block;
      width: 100%;
    }

    ul li {
      a {
        height: 40px;
        padding: 8px 11px;
      }
    }
  }
}
</style>
