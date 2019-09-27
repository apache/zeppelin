<template>
  <div>
    <div v-show="forceEditorShow" class="md-editor">
      <div class="row">
        <div class="col-6 br pr-0">
          <!-- <textarea class="md-editor-textarea" :value="mdValue" @input="update" v-on:blur="setMDParagraph"></textarea> -->
          <MDEditor :forceEditorShow="forceEditorShow" :content="compiledMarkdown" :onMdUpdate="onMdUpdate" :setMDParagraph="setMDParagraph"/>
        </div>
        <div class="col-6">
          <div class="md-op" v-html="compiledMarkdown"></div>
        </div>
      </div>
    </div>
    <div v-show="!hideEditor" class="editor-wrapper">
      <span class="controls">
        <span v-show="!getParagraphLoading && !getParagraphInit" v-on:click="runParagraph()" class="play">
          <a-icon type="play-circle" />
        </span>
        <div v-show="getParagraphLoading" class="lds-ring"><div></div><div></div><div></div><div></div></div>
        <div v-show="getParagraphInit" class="lds-ring pending-add-paragraph"><div></div><div></div><div></div><div></div></div>
      </span>
      <div v-on:keyup.shift.enter="runParagraph()" class="editor-main">
        <a-dropdown>
          <a class="ant-dropdown-link" href="#">
            <a-icon type="ellipsis" />
          </a>
          <a-menu slot="overlay">
            <a-menu-item>
              <a href="javascript:;">Toggle Editor</a>
            </a-menu-item>
            <a-menu-item>
              <a href="javascript:;">Toggle Line Numbers</a>
            </a-menu-item>
            <a-menu-item>
              <a href="javascript:;">Toggle Title</a>
            </a-menu-item>
            <a-menu-item>
              <a href="javascript:;">Clear Output</a>
            </a-menu-item>
            <a-menu-divider />
            <a-menu-item>
              <a
                @click="removeParagraph()"
                href="javascript:;"
              >
                Delete Paragraph
              </a>
            </a-menu-item>
          </a-menu>
        </a-dropdown>

        <AceEditor :ref="setEditorId()" v-model="content" @init="editorInit" lang="scala" theme="chrome" height="50" :options="setOptions"/>
      </div>
    </div>
  </div>
</template>

<script>
// import _ from 'underscore'
import wsFactory from '@/services/ws-factory.js'
import marked from 'marked'
import MDEditor from './MDEditor.vue'

export default {
  name: 'Editor',
  components: {
    'AceEditor': require('vue2-ace-editor'),
    'MDEditor': MDEditor
  },
  watch: {
    content: function (newContent) {

    }
  },
  props: ['paragraph', 'noteId'],
  data () {
    return {
      mdValue: this.$props.paragraph.text,
      content: this.$props.paragraph.text,
      defaultConfig: {
        'maxLines': Infinity,
        'showGutter': true
      }
    }
  },
  computed: {
    setOptions: function () {
      const p = this.$store.getters.getParagraphById(this.$props.paragraph.id, this.$props.noteId)

      if (p.config.lineNumbers !== undefined) {
        return {
          'showGutter': p.config.lineNumbers
        }
      }

      return {
        'showGutter': true
      }
    },
    compiledMarkdown: function () {
      let withoutMdMark = this.mdValue.replace('%md\n', '')
      return marked(withoutMdMark, {
        headerPrefix: 'toc_'
      })
    },
    getParagraphLoading: function () {
      const { id } = this.$props.paragraph
      const isLoading = this.$store.getters.isParagraphLoading(id, this.$props.noteId)

      return isLoading
    },
    getParagraphInit: function () {
      const { id } = this.$props.paragraph
      const isPending = this.$store.getters.isParagraphCreating(id, this.$props.noteId)

      return isPending
    },
    forceEditorShow: function () {
      const { id } = this.$props.paragraph
      const paragraph = this.$store.getters.getParagraphById(id, this.$props.noteId)

      if (paragraph && paragraph.forceEditorShow) {
        return true
      }

      return false
    },
    hideEditor: function () {
      /*
      * hide editor if the paragraph is
      * md
      * hide editor if the paragraph config
      * has hide editor flag
      * add more conditions later here if
      * required
      */
      const { id } = this.$props.paragraph
      const paragraph = this.$store.getters.getParagraphById(id, this.$props.noteId)

      if (paragraph &&
      paragraph.results &&
      paragraph.results.msg &&
      paragraph.results.msg[0] &&
      paragraph.results.msg[0].type.toLowerCase() === 'html') {
        return true
      }

      if (paragraph) {
        return paragraph.config.editorHide
      }

      return false
    }
  },
  methods: {
    onMdUpdate: function (md) {
      this.mdValue = md
    },
    setMDParagraph: function () {
      let { id } = this.$props.paragraph
      let paragraph = this.$store.getters.getParagraphById(id, this.$props.noteId)
      let mdValue = this.mdValue

      if (paragraph &&
        paragraph.results &&
        paragraph.results.msg &&
        paragraph.results.msg[0]) {
        paragraph.results.msg[0].data = mdValue

        // if its empty then delete the md paragraph
        if (mdValue === '' || mdValue.trim() === '%md') {
          this.removeParagraph()
          return
        }

        this.$store.dispatch('setParagraph', {
          noteId: this.$props.noteId,
          paragraph: paragraph
        })

        this.$store.dispatch('setParagraphProp', {
          id: id,
          noteId: this.$props.noteId,
          prop: {
            name: 'forceEditorShow',
            value: false
          }
        })

        // run paragraph to presist
        this.runParagraph()
      }
    },
    editorInit: function (editor) {
      require('brace/ext/language_tools')
      require('brace/mode/html')
      require('brace/mode/scala')
      require('brace/mode/less')
      require('brace/theme/chrome')
      require('brace/snippets/javascript')

      editor.setOptions(this.defaultConfig)
    },
    setEditorId: function () {
      return 'editor_' + this.$props.paragraph.id
    },
    runParagraph: function () {
      let { id, title, config, settings } = this.$props.paragraph
      let paragraphText = this.content

      // append md marker back
      // before saving
      // for md para
      if (this.hideEditor) {
        paragraphText = '%md\n' + this.mdValue
      }

      wsFactory.getConn(this.$props.noteId).send({
        op: 'RUN_PARAGRAPH',
        data: {
          id: id,
          title: title,
          paragraph: paragraphText,
          config: config,
          params: settings.params
        }
      })
    },
    removeParagraph: function () {
      let { id } = this.$props.paragraph

      // remove from ui instantly
      this.$store.dispatch('removeParagraph', {
        id: id,
        noteId: this.$props.noteId
      })

      wsFactory.getConn(this.$props.noteId).send({
        op: 'PARAGRAPH_REMOVE',
        data: {
          id: id
        }
      })
    }
  },
  updated: function () {

  },
  mounted: function () {
    // this.$nextTick(function () {
    //   const { id } = this.$props.paragraph
    //   let editor = this.$refs['editor_' + id].editor
    //   editor.getSession().on('change', function() {
    //     console.log('called')
    //     resize();
    //   })

    //   let resize = function() {
    //     let height = editor.getSession().getScreenLength() * editor.renderer.lineHeight + editor.renderer.scrollBar.getWidth()
    //     document.getElementsByClassName('editor-main')[0].style.height = height.toString() + 'px'
    //     editor.resize();
    //   }
    // })
  }
}
</script>

<style lang="scss">
  .md-paragraph ul li p {
    margin: 0;
  }

  .ace_content {
    background: #f7f7f7;
  }

  .ace-chrome {
    position: relative;
    bottom: 10px;
  }

  .ace-chrome .ace_marker-layer .ace_active-line {
    background: #ebf3fd !important;
  }

  .ace-chrome .ace_gutter-active-line {
    background: transparent !important;
  }

  .ace-chrome .ace_gutter {
    background: #f7f7f7 !important;
    border-right: 1px solid #ebebeb;
  }

  .options {
    float: right;
    margin-right: 10px;
    color: #6b6b6c;
    cursor: pointer;
  }

  .options:hover {
    color: #007bff;
  }

  .editor-wrapper {
    background: #e7e7e7;
    padding: 0 0 0 40px;
    border-radius: 1px;
  }

  .editor-main {
    background: #f7f7f7;
    position: relative;

    .ant-dropdown-link {
      position: absolute;
      right: 0px;
      top: 0px;
      z-index: 999;
      margin: 2px 8px;
    }
  }

  .editor-wrapper:hover,
  .editor-wrapper:focus,
  .editor-wrapper:active {
    box-shadow: 0 4px 5px 0 rgba(0, 0, 0, 0.14), 0 1px 10px 0 rgba(255, 255, 255, 0.12), 0 2px 4px -1px rgba(255, 255, 255, 0.4);
  }

  .controls {
    top: 9px;
    position: relative;
    right: 32px;
    float: left;
  }

  .controls .play {
    cursor: pointer;
  }

  .controls .play svg {
    color: #656566;
  }

  .controls .play svg:hover {
    color: #007bff;
  }

  .lds-ring {
    display: inline-block;
    position: relative;
    bottom: 18px;
    right: 4px;
    width:0px;
    height: 0px;
  }

  .lds-ring div {
    box-sizing: border-box;
    display: block;
    position: absolute;
    width: 20px;
    height: 20px;
    margin: 6px;
    border: 2px solid #fff;
    border-radius: 50%;
    animation: lds-ring 1.2s cubic-bezier(0.5, 0, 0.5, 1) infinite;
    border-color: #007bff transparent transparent transparent;
  }

  .lds-ring div:nth-child(1) {
    animation-delay: -0.45s;
  }

  .lds-ring div:nth-child(2) {
    animation-delay: -0.3s;
  }

  .lds-ring div:nth-child(3) {
    animation-delay: -0.15s;
  }

  @keyframes lds-ring {
    0% {
      transform: rotate(0deg);
    }
    100% {
      transform: rotate(360deg);
    }
  }

  .md-editor {

  }

  .md-editor ul li p {
    margin: 0;
  }

  .md-editor .br {
    border-right: 1px dashed #9b9494;
  }

  .md-editor .md-op {
    padding: 20px;
  }

  .pending-add-paragraph div {
    border-color: #2DC76D transparent transparent transparent;
  }

  .md-editor textarea {
    width: 100%;
    height: 274px;
    padding: 20px;
    border: 0px;
    margin-top: 0px;
    outline: 0;
    margin-bottom: 0px;
  }
</style>
