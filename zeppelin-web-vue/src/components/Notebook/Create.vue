<template>
  <div>
    <a-modal
        v-model="showDialog"
        v-bind:title="this.actionLabel + ' Notebook'"
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
          label="Notebook Name"
        >
          <a-input placeholder="Enter Notebook Name"  v-model="name"/>
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

        <input type="hidden" v-model="sourceNotebookId" name="sourceNotebookId" value="" />

      </a-form>
    </a-modal>
  </div>
</template>

<script>
import { EventBus } from '@/services/event-bus'

export default {
  name: 'CreateNotebook',
  data () {
    return {
      showDialog: false,
      loading: false,

      name: '',
      defaultInterpreter: null,
      sourceNotebookId: '',

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
    EventBus.$on('showCreateNotebookDialog', () => {
      this.action = 'create'
      this.actionLabel = 'Create'

      this.sourceNotebookId = ''
      this.showDialog = true
    })

    EventBus.$on('showCloneNotebookDialog', (sourceNotebookId) => {
      this.action = 'clone'
      this.actionLabel = 'Clone'

      this.sourceNotebookId = sourceNotebookId

      this.showDialog = true
    })
  },
  methods: {
    handleOk (e) {
      this.loading = true

      switch (this.action) {
        case 'create':
          this.$root.executeCommand('notebook', this.action, {
            name: this.name,
            defaultInterpreter: this.defaultInterpreter
          })
          break
        case 'clone':
          this.$root.executeCommand('notebook', this.action, {
            newNotebookName: this.name,
            sourceNotebookId: this.sourceNotebookId
          })
          break
      }

      let that = this
      setTimeout(() => {
        this.showDialog = false
        this.loading = false

        this.resetForm()

        that.$message.success(that.$i18n.t('message.notebooks.' + this.action + '_success'), 4)
        // Pending - validation
        // Pending open created/cloned Notebook
      }, 1000)
    },
    handleCancel (e) {
      this.showDialog = false
    },
    resetForm () {
      this.name = ''
      this.defaultInterpreter = ''
      this.sourceNotebookId = ''
    }
  }
}
</script>

<style lang="scss">
</style>
