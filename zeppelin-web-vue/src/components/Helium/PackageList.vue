<template>
  <div class="packages">
    <div
      v-if="isLoading"
    >
      <div
        v-for="index in 3"
        :key="index"
        class="timeline-item"
      >
        <div class="animated-background">
          <div class="background-masker pkg-label-separator"></div>
        </div>
      </div>
    </div>

    <ul>
      <li
        v-for="(pkg, index) in this.$store.state.HeliumStore.packages"
        :key="index"
        class="package"
      >
        <a
          href="javascript: void(0);"
          v-bind:title="index"
          class="text-ellipsis"
        >
          {{ index }}
        </a>
      </li>
    </ul>
  </div>
</template>

<script>
import { mapState } from 'vuex'

export default {
  name: 'PackageList',
  data () {
    return {
      isLoading: false
    }
  },
  computed: {
    ...mapState({
      packages: 'HeliumStore/packages'
    })
  },
  mounted () {
    this.getPackages()
  },

  methods: {
    getPackages () {
      this.isLoading = true
      this.$store.dispatch('getPackages')
        .then(() => {
          this.isLoading = false
        })
    }
  }
}
</script>

<style lang="scss" scoped>
.timeline-item {
  padding: 0 12px;
  margin: 9px auto;
  height: 20px;

  .pkg-label-separator {
    left: 20px;
    top: 0;
    width: 4px;
    height: 24px;
  }
}

.packages {
  list-style: none;
  margin: 0;
  padding: 0;

  ul {
    list-style: none;
    margin: 0;
    padding: 0;

    li {
      a {
        font-size: 14px;
        padding: 5px 10px;
        display: block;
        border-left: 4px solid transparent;

        &.active {
          background: #f1eeee;
          border-left-color: #2f71a9;
        }

        &:hover {
          background: #F1F1F1;

        }
      }
    }
  }
}
</style>
