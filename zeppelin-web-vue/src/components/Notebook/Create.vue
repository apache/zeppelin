<template>
  <div>
    <a-modal
        v-model="showDialog"
        v-bind:title="this.actionLabel + ' Note'"
        onOk="handleOk"
        :maskClosable="false"
      >
      <template slot="footer">
        <a-button key="back" @click="handleCancel">Cancel</a-button>
        <a-button key="submit" type="primary" :loading="loading" @click="handleOk">
          {{ this.actionLabel }}
        </a-button>
      </template>

      <a-form layout="vertical">
        <a-form-item
          label="Note Name"
        >
          <a-input placeholder="Enter Note Name"  v-model="name"/>
        </a-form-item>

        <a-form-item
          v-if="action === 'create'"
          label="Default Interpreter"
        >
          <a-select
            showSearch
            style="width: 180px"
            :defaultValue="interpreters[0]"
            v-model="defaultInterpreter"
          >
            <a-select-option
              v-for="interpreter in interpreters"
              v-bind:key="interpreter.id"
            >
              {{interpreter.id}}
            </a-select-option>
          </a-select>
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
  name: 'CreateNote',
  data () {
    return {
      showDialog: false,
      loading: false,

      name: '',
      defaultInterpreter: null,
      sourceNoteId: '',

      action: 'create',
      actionLabel: 'Create'
    }
  },
  computed: {
    interpreters () {
      return this.$store.state.InterpreterStore.interpreters
    }
  },
  mounted () {
    EventBus.$on('showCreateNoteDialog', () => {
      this.action = 'create'
      this.actionLabel = 'Create'

      this.sourceNoteId = ''
      this.showDialog = true
    })

    EventBus.$on('showCloneNoteDialog', (sourceNoteId) => {
      this.action = 'clone'
      this.actionLabel = 'Clone'

      this.sourceNoteId = sourceNoteId

      this.showDialog = true
    })
  },
  methods: {
    handleOk (e) {
      this.loading = true

      switch (this.action) {
        case 'create':
          this.$root.executeCommand('note', this.action, {
            name: this.name,
            defaultInterpreter: this.defaultInterpreter
          })
          break
        case 'clone':
          this.$root.executeCommand('note', this.action, {
            newNoteName: this.name,
            sourceNoteId: this.sourceNoteId
          })
          break
      }

      let that = this
      setTimeout(() => {
        this.showDialog = false
        this.loading = false

        this.resetForm()

        that.$message.success(that.$i18n.t('message.note.' + this.action + '_success'), 4)
        // Pending - validation
        // Pending open created/cloned Note
      }, 1000)
    },
    handleCancel (e) {
      this.showDialog = false
    },
    resetForm () {
      this.name = ''
      this.defaultInterpreter = ''
      this.sourceNoteId = ''
    }
  }
}
</script>

<style lang="scss">
</style>
