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
              <a-menu-divider />
              <a-menu-item>
                <a
                  href="javascript: void(0);"
                  @click="executeNoteCommand('restore-note', note.id)"
                >
                  Restore
                </a>
              </a-menu-item>
              <a-menu-item>
                <a
                  href="javascript: void(0);"
                  @click="showDeletePermConfirm(note.id)"
                >
                  Delete Permanently
                </a>
              </a-menu-item>
            </a-menu>
          </a-dropdown>
        </a>
      </li>
    </ul>

    <div
      v-if="this.notes.length === 0"
      class="pt-2 pl-2"
    >
      {{ $t("message.note.empty_trash") }}
    </div>
  </div>
</template>

<script>
export default {
  name: 'Trash',
  computed: {
    isLoading () {
      return this.$store.state.NotebookStore.isListLoading
    },
    activeNoteId () {
      return this.$store.state.TabManagerStore.currentTab && this.$store.state.TabManagerStore.currentTab.id
    },
    notes () {
      return this.$store.state.NotebookStore.notes.filter(n => (n.path ? n.path.split('/')[1] === this.$root.TRASH_FOLDER_ID : false))
    }
  },
  methods: {
    executeNoteCommand (command, args) {
      this.$root.executeCommand('note', command, args)
    },
    openNote (note) {
      this.$root.executeCommand('tabs', 'open', {
        type: 'note',
        note: note
      })
    },
    showDeletePermConfirm (noteId) {
      let that = this
      this.$confirm({
        title: that.$i18n.t('message.note.delete_confirm'),
        content: that.$i18n.t('message.note.delete_content'),
        onOk () {
          that.executeNoteCommand('delete-permanently', noteId)

          that.$message.success(that.$i18n.t('message.note.delete_success'), 4)
        },
        onCancel () {}
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
