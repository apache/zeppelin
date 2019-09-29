  <template>
  <div>
    <a-modal
        v-model="showDialog"
        title="Import Note"
        onOk="handleOk"
        :maskClosable="false"
      >
      <template slot="footer">
        <a-button key="back" @click="handleCancel">Cancel</a-button>
        <a-button key="submit" type="primary" :loading="loading" @click="handleOk">Import</a-button>
      </template>

      <a-form layout="vertical">
        <a-form-item>
          <a-radio-group
            v-model="importType"
            buttonStyle="solid"
            name="import-type"
            @change="changeImportType"
            style="display: block; margin: 0 auto; width: 238px;"
          >
            <a-radio-button value="url">Provide a URL</a-radio-button>
            <a-radio-button value="file">Upload a File</a-radio-button>
          </a-radio-group>
        </a-form-item>

        <div
          v-if="importType === 'file'"
          id="note-import-drag-drop"
          class="ant-upload ant-upload-drag"
          v-bind:class="{'hover': isFileDragHover}"
          style="margin-bottom: 16px; display: block;"
          @click="showFileDialog"
          @change="handleChange"
          @drop.prevent="handleChange"
          @dragover.prevent="handleDragHover"
          @dragleave.prevent="handleDragHover"
        >
          <input
            type="file"
            name="nbFileInput"
            ref="nbFileInput"
            style="display: none;"
          />
          <p class="ant-upload-drag-icon" style="margin-bottom: 10px;">
            <a-icon type="inbox" />
          </p>
          <p class="ant-upload-text" style="font-size: 13px;">
            <span v-if="sourceNoteFile">{{ this.sourceNoteFile.name }}</span>
            <span v-else>{{ $t("message.note.import_click_or_drag") }}</span>
          </p>
        </div>

        <a-form-item
          v-if="importType === 'url'"
          label="Note URL"
        >
          <a-input placeholder="Note URL"  v-model="noteURL"/>
        </a-form-item>

        <a-form-item
          label="Note Name"
        >
          <a-input placeholder="Enter Note Name"  v-model="name"/>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script>
import { EventBus } from '@/services/event-bus'

export default {
  name: 'ImportNote',
  data () {
    return {
      showDialog: false,
      loading: false,
      isFileDragHover: false,

      name: '',

      importType: 'file',
      sourceNoteFile: null,
      sourceNoteJSON: null,
      fileUploaded: false
    }
  },
  mounted () {
    EventBus.$on('showImportNoteDialog', () => {
      this.showDialog = true
    })
  },
  methods: {
    showFileDialog () {
      this.$refs.nbFileInput.click()
    },
    handleChange (e) {
      this.handleDragHover(e)

      let files = e.target.files || e.dataTransfer.files
      let validated = files.length !== 0 && this.beforeUpload(files[0])
      if (validated) {
        this.sourceNoteFile = files[0]

        let reader = new FileReader()
        reader.readAsText(this.sourceNoteFile)
        reader.onloadend = () => {
          this.sourceNoteJSON = JSON.parse(reader.result)
          this.fileUploaded = true
        }
      } else {
        this.sourceNoteFile = null
        this.sourceNoteJSON = null
        this.fileUploaded = false
      }
    },
    beforeUpload (file) {
      const isJSON = (file.type === 'application/json')
      if (!isJSON) {
        this.$message.error(this.$i18n.t('message.note.import_json_type_error'), 4)
      }
      const isLt1M = file.size / 1024 / 1024 < 1
      if (!isLt1M) {
        this.$message.error(this.$i18n.t('message.note.import_json_size_error'), 4)
      }
      return isJSON && isLt1M
    },
    handleOk (e) {
      this.loading = true

      this.sourceNoteJSON.name = this.name
      this.$root.executeCommand('note', 'import-json', this.sourceNoteJSON)

      setTimeout(() => {
        this.showDialog = false
        this.loading = false

        this.resetForm()

        this.$message.success(this.$i18n.t('message.note.import_success'), 4)
        // Pending - validation
        // Pending open - imported Note
      }, 1000)
    },
    handleCancel (e) {
      this.showDialog = false
      this.resetForm()
    },
    resetForm () {
      this.name = ''

      this.importType = 'file'
      this.sourceNoteFile = null
      this.sourceNoteJSON = null
      this.fileUploaded = false
    },
    changeImportType (e) {
      this.importType = e.target.value
    },
    handleDragHover (e) {
      this.isFileDragHover = e.type === 'dragover'
    }
  }
}
</script>

<style lang="scss" scoped>
.hover {
  border-color: #505050 !important;
}
</style>
