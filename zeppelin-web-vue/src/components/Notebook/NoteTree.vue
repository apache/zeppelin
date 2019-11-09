<template>
  <div class="notes">
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

    <a-input-search
      v-if="!isLoading"
      placeholder="search notebooks"
      class="search-notebook-box"
      @search="onSearch"
    />

    <ul>
      <li
        v-for="(note, index) in this.notes"
        :key="index"
        class="note"
      >
        <a
          href="javascript: void(0);"
          v-bind:title="note.path"
          class="text-ellipsis"
          :class="{'active':  note.id === activeNoteId}"
          @click="openNote(note)"
        >
          <a-icon type="file" />
          <span>{{ getFileName(note.path) }}</span>

          <a-dropdown
            class="note-menu"
            placement="bottomRight"
          >
            <a
              class="ant-dropdown-link"
              href="javascript: void(0);"
            >
              <a-icon type="ellipsis" />
            </a>
            <a-menu slot="overlay">
              <a-menu-item>
                <a
                  href="javascript: void(0);"
                  @click="openNote(note)"
                >
                  Open Notebook
                </a>
              </a-menu-item>
              <a-menu-item>
                <a
                  href="javascript: void(0);"
                  @click="showRenameDialog(note)"
                >
                  Rename
                </a>
              </a-menu-item>
              <a-menu-divider />
              <a-menu-item>
                <a
                  href="javascript: void(0);"
                  @click="showMoveToTrashConfirm(note.id)"
                >
                  Move to Trash
                </a>
              </a-menu-item>
            </a-menu>
          </a-dropdown>
        </a>
      </li>
    </ul>
  </div>
</template>

<script>
import ws from '@/services/ws-helper'

export default {
  name: 'NoteTree',
  data () {
    return {

    }
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
    notes () {
      return this.$store.state.NotebookStore.notes.filter(n => (n.path ? n.path.split('/')[1] !== this.$root.TRASH_FOLDER_ID : false))
    }
  },
  methods: {
    openNote (note) {
      this.$root.executeCommand('tabs', 'open', {
        type: 'note',
        note: note
      })
      this.$router.push({ name: 'TabManagerNoteView', params: { tabId: note.id } })
    },
    getFileName (path) {
      return path.substr(path.lastIndexOf('/') + 1)
    },
    onSelect (keys) {
      console.log('Trigger Select', keys)
    },
    onSearch (value) {
      // a
    },
    showMoveToTrashConfirm (noteId) {
      let that = this
      this.$confirm({
        title: that.$i18n.t('message.note.move_to_trash_confirm'),
        content: that.$i18n.t('message.note.move_to_trash_content'),
        onOk () {
          that.$root.executeCommand('note', 'move-to-trash', noteId)

          that.$message.success(that.$i18n.t('message.note.move_to_trash_success'), 4)
        },
        onCancel () {}
      })
    },
    showRenameDialog (note) {
      this.$root.executeCommand('showRenameNoteDialog', {
        id: note.id,
        path: note.path
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

  .nb-label-separator {
    left: 20px;
    top: 0;
    width: 4px;
    height: 24px;
  }
}

.search-notebook-box {
  padding: 8px 6px;
  width: calc(100%);
}

.notes {
  list-style: none;
  margin: 0;
  padding: 0;

  ul {
    list-style: none;
    margin: 0;
    padding: 0;

    li.note {
      position: relative;

      a {
        font-size: 14px;
        padding: 5px 10px;
        display: flex;
        border-left: 4px solid transparent;

        i {
          line-height: 20px;
        }

        &> span {
          position: relative;
          padding-left: 5px;
        }

        &.active {
          background: #f1eeee;
          border-left-color: #2f71a9;
        }

        &:hover {
          background: #F1F1F1;
        }
      }

      a.note-menu {
        position: absolute;
        top: 5px;
        right: 5px;
        padding: 0;

        i {
          transform: rotate(90deg);
        }
      }
    }
  }
}
</style>
