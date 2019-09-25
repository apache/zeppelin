<template>
  <div
    class="preferences"
    :class="{'show': showPrefererences}"
  >
    <div class="headline">
      Preferences
    </div>
    <a
      class="close"
      href="javascript: void(0);"
      @click="hidePreferences()"
    >
      <i class="icon-close" />
    </a>
  </div>
</template>

<script>

export default {
  name: 'Preferences',
  components: {

  },
  computed: {
    showPrefererences () {
      return this.$store.state.showPrefererences
    }
  },
  methods: {
    hidePreferences () {
      this.$store.dispatch('togglePreferences', false)
    }
  }
}
</script>

<style lang="scss" scoped>
.preferences {
  bottom: 28px;
  left: 48px;
  display: none;

  width: 260px;
  min-height: 300px;
  cursor: auto;
  transition: all .2s ease;
  background-color: #f3f3f3;
  border-radius: 0 8px 0 0;
  font-size: 13px;
  font-weight: 400;
  position: absolute;
  text-align: left;
  z-index: 999;

  &.show {
    display: block;
  }

  .headline {
    margin: 0 auto;
    font-size: 16px;
    font-family: Open Sans;
    text-align: center;
    height: 44px;
    line-height: 44px;
  }

  .close {
    position: absolute;
    right: 8px;
    top: 8px;
    z-index: 2120;
    cursor: pointer;
    width: 28px;
    height: 28px;
    opacity: .6;
    transition: all .15s linear;

    svg {
      color: #333;
    }

    .icon-close {
      background: url("data:image/svg+xml,%3Csvg fill='%23616161' height='16' viewBox='0 0 24 24' width='16' xmlns='http://www.w3.org/2000/svg'%3E%3Cpath d='M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z'/%3E%3Cpath d='M0 0h24v24H0z' fill='none'/%3E%3C/svg%3E") no-repeat;
      height: 20px;
      width: 20px;
      display: block;
    }

    &:hover {
      opacity: 1!important;
    }
  }
}
</style>
