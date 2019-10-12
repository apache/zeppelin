<template>
  <div>
    <a-modal
        v-model="showDialog"
        title="Rename Note"
        onOk="handleOk"
        :maskClosable="false"
      >
      <template slot="footer">
        <a-button key="back" @click="handleCancel">Cancel</a-button>
        <a-button key="submit" type="primary" :loading="loading" @click="handleOk">
          Rename
        </a-button>
      </template>

      <a-form layout="vertical">
        <a-form-item
          label="Note Name"
        >
          <a-input placeholder="Enter Note Name"  v-model="name"/>
        </a-form-item>

        <a-alert message="Use '/' to create folders. Example: /NoteDirA/Note1" type="info" />

        <input type="hidden" v-model="sourceNoteId" name="sourceNoteId" value="" />

      </a-form>
    </a-modal>
  </div>
</template>

<script>
import { EventBus } from '@/services/event-bus'

export default {
  name: 'RenameNote',
  data () {
    return {
      showDialog: false,
      loading: false,

      name: '',
      sourceNoteId: ''
    }
  },
  computed: {
    interpreters () {
      return this.$store.state.InterpreterStore.interpreters
    }
  },
  mounted () {
    EventBus.$on('showRenameNoteDialog', (note) => {
      this.sourceNoteId = note.id
      this.name = note.path
      this.showDialog = true
    })
  },
  methods: {
    handleOk (e) {
      this.loading = true

      this.$root.executeCommand('note', 'rename', {
        newNoteName: this.name,
        sourceNoteId: this.sourceNoteId
      })

      let that = this
      setTimeout(() => {
        this.showDialog = false
        this.loading = false

        this.resetForm()

        that.$message.success(that.$i18n.t('message.note.rename_success'), 4)
        // Pending - validation
        // Pending update everywhere
      }, 1000)
    },
    handleCancel (e) {
      this.showDialog = false
    },
    resetForm () {
      this.name = ''
      this.sourceNoteId = ''
    }
  }
}
</script>

<style lang="scss" scoped>
</style>
