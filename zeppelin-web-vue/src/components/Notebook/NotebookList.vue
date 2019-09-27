<template>
  <div class="notebooks">
    <div
      v-if="isLoading"
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

    <ul>
      <li
        v-for="(note, index) in this.notebooks"
        :key="index"
        class="notebook"
      >
        <a
          href="javascript: void(0);"
          v-bind:title="note.path"
          class="text-ellipsis"
          :class="{'active':  note.id === activeNoteId}"
          v-on:click="openNotebook(note)"
        >
          <a-icon type="file" />
          {{ getFileName(note.path) }}
        </a>
      </li>
    </ul>
  </div>
</template>

<script>
import ws from '@/services/ws-helper'

export default {
  name: 'StatusBar',
  data () {
    return {
      notebookTree: [{
        title: 'Basic Notebook',
        key: '2BKAJALA32A',
        children: [{
          title: 'parent 1-0',
          key: '0-0-0',
          isLeaf: true
        }, {
          title: 'parent 1-1',
          key: '0-0-1',
          children: [
            { key: '0-0-1-0', slots: { title: 'title0010' } }
          ]
        }
        ]
      }, {
        title: 'Basi1c Notebook',
        key: '2BKAaJALA32A',
        isLeaf: true
      }]
    }
  },
  components: {

  },
  created () {

  },
  mounted () {
    ws.getConn().send({ op: 'LIST_NOTES' })
  },
  computed: {
    isLoading () {
      return this.$store.state.NotebookStore.isListLoading
    },
    activeNoteId () {
      return this.$store.state.TabManagerStore.currentTab && this.$store.state.TabManagerStore.currentTab.id
    },
    notebooks () {
      return this.$store.state.NotebookStore.notebooks
    }
  },
  methods: {
    openNotebook (notebook) {
      this.$root.executeCommand('tabs', 'open', {
        type: 'notebook',
        notebook: notebook
      })
    },
    getFileName (path) {
      return path.substr(path.lastIndexOf('/') + 1)
    }
  }
}
</script>

<style lang="scss" scoped>
.timeline-item {
  padding: 0 12px;
  margin: 9px auto;
  height: 20px;

  .nb-label-separator {
    left: 20px;
    top: 0;
    width: 4px;
    height: 24px;
  }
}

.notebooks {
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
