<template>
  <div class="activity-console">
    <div
      v-if="isActivityLoading"
    >
      <div
        v-for="index in 3"
        :key="index"
        class="timeline-item"
      >
        <div class="animated-background">
          <div class="background-masker nb-label-separator"></div>
        </div>
      </div>
    </div>

    <ul v-else>
      <li
        v-for="(activity, index) in this.activities"
        :key="index"
        class="note"
      >
        <a
          href="javascript: void(0);"
          v-bind:title="activity.noteName"
          class="text-ellipsis"
        >
          <a-icon type="file" />
          {{ activity.noteName }}
        </a>
      </li>
    </ul>
  </div>
</template>

<script>
import ws from '@/services/ws-helper'

export default {
  name: 'ActivityConsole',
  computed: {
    activities () {
      return this.$store.state.activities
    },
    isActivityLoading () {
      return this.$store.state.isActivityLoading
    }
  },
  mounted () {
    this.getActivities()
  },
  methods: {
    getActivities () {
      ws.getConn().send({ op: 'LIST_NOTE_JOBS' })
    }
  }
}
</script>

<style lang="scss" scoped>
.activity-console {
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
