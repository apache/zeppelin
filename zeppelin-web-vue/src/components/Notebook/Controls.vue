<template>
  <div class="note-controls">
    <div class="left-controls">
      <a
        href="javascript: void(0);"
        @click="executeNoteCommand('run-all')"
        :disabled="isDeleted"
      >
        <a-tooltip placement="top">
          <template slot="title">
            <span>Run all paragraphs</span>
          </template>
          <a-icon type="play-circle" />
        </a-tooltip>
      </a>

      <a
        href="javascript: void(0);"
        @click="executeNoteCommand('save')"
        :disabled="isDeleted"
      >
        <a-tooltip placement="top">
          <template slot="title">
            <span>Save</span>
          </template>
          <a-icon type="save" />
        </a-tooltip>
      </a>

      <a
        href="javascript: void(0);"
        @click="executeNoteCommand('show-clone')"
        :disabled="isDeleted"
      >
        <a-tooltip placement="top">
          <template slot="title">
            <span>Clone this note</span>
          </template>
          <a-icon type="copy" />
        </a-tooltip>
      </a>

      <a
        href="javascript: void(0);"
        @click="executeNoteCommand('export-json')"
        :disabled="isDeleted"
      >
        <a-tooltip placement="top">
          <template slot="title">
            <span>Export</span>
          </template>
          <a-icon type="download" />
        </a-tooltip>
      </a>

      <a
        href="javascript: void(0);"
        @click="showMoveToTrashConfirm"
        v-if="!isDeleted"
      >
        <a-tooltip placement="top">
          <template slot="title">
            <span>Move to Trash</span>
          </template>
          <a-icon type="delete" />
        </a-tooltip>
      </a>

      <a
        href="javascript: void(0);"
        @click="executeNoteCommand('restore-note')"
        v-if="isDeleted"
      >
        <a-tooltip placement="top">
          <template slot="title">
            <span>Restore</span>
          </template>
          <a-icon type="reload" />
        </a-tooltip>
      </a>

      <a
        href="javascript: void(0);"
        @click="showDeletePermConfirm"
        v-if="isDeleted"
      >
        <a-tooltip placement="top">
          <template slot="title">
            <span>Delete Permanently</span>
          </template>
          <a-icon type="delete" />
        </a-tooltip>
      </a>
    </div>

    <div class="right-controls">
      <a
        href="javascript: void(0);"
        :disabled="isDeleted"
      >
        <a-tooltip placement="top">
          <template slot="title">
            <span>Search Code</span>
          </template>
          <a-icon type="file-search" />
        </a-tooltip>
      </a>

      <a
        href="javascript: void(0);"
        :disabled="isDeleted"
      >
        <a-tooltip placement="top">
          <template slot="title">
            <span>Version Control</span>
          </template>
          <a-icon type="diff" />
        </a-tooltip>
      </a>

      <a-dropdown
        :disabled="isDeleted"
        :class="{disabled: isDeleted}"
        :trigger="['click']"
      >
        <a class="ant-dropdown-link" href="#">
          <span> Default </span>
          <a-icon type="down" />
        </a>
        <a-menu slot="overlay">
          <a-menu-item key="0">
            <a href="javascript: void(0)">Default</a>
          </a-menu-item>
          <a-menu-item key="1">
            <a href="javascript: void(0)">Simple</a>
          </a-menu-item>
          <a-menu-item key="2">
            <a href="javascript: void(0)">Report</a>
          </a-menu-item>
        </a-menu>
      </a-dropdown>
    </div>
  </div>
</template>

<script>
export default {
  name: 'NoteControls',
  props: {
    noteId: { required: true }
  },
  computed: {
    isDeleted () {
      let notePath = this.$store.getters.getNote(this.$props.noteId).path
      return notePath ? notePath.split('/')[1] === this.$root.TRASH_FOLDER_ID : false
    }
  },
  methods: {
    executeNoteCommand (command, args) {
      this.$root.executeCommand('note', command, args)
    },
    showMoveToTrashConfirm () {
      let that = this
      this.$confirm({
        title: that.$i18n.t('message.note.move_to_trash_confirm'),
        content: that.$i18n.t('message.note.move_to_trash_content'),
        onOk () {
          that.executeNoteCommand('move-to-trash')

          that.$message.success(that.$i18n.t('message.note.move_to_trash_success'), 4)
        },
        onCancel () {}
      })
    },
    showDeletePermConfirm () {
      let that = this
      this.$confirm({
        title: that.$i18n.t('message.note.delete_confirm'),
        content: that.$i18n.t('message.note.delete_content'),
        onOk () {
          that.executeNoteCommand('delete-permanently')

          that.$message.success(that.$i18n.t('message.note.delete_success'), 4)
        },
        onCancel () {}
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.note-controls {
  height: 30px;
  background: #f7f7f7;

  a {
    display: inline-block;
    height: 100%;
    padding: 2px 7px;

    span {
      vertical-align: middle;
    }

    &.disabled {
      cursor: default;
      color: inherit;
      opacity: 0.5;
    }
  }

  .left-controls {
    float: left;
    padding-left: 7px;
    height: 100%;
    display: flex;
  }

  .right-controls {
    float: right;
    padding-right: 7px;
    height: 100%;
    display: flex;
  }
}
</style>
