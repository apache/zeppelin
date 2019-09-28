<template>
  <div v-on:click="setActive(paragraph)" :id="'paragraph-' + paragraph.id" class="paragraph-main">
    <div class="paragraph mb-3">
      <div class="paragraph-body">
        <h5 v-show="hasTitle">
          {{paragraph.title}}
        </h5>

        <Editor
          :paragraph="paragraph"
          :isMdEditorVisible="isMdEditorVisible"
          :noteId="noteId"
        />
        <div
          v-if="hasResults"
          class="results-multi"
        >
          <Results
            :key="index"
            v-for="(result, index) in getResults"
            :result="result"
            :index="index"
            :paragraph="paragraph"
            :noteId="noteId"
          />
        </div>
      </div>
    </div>

    <AddParagraph
      :index="index"
      :noteId="noteId"/>
  </div>
</template>

<script>
import Results from './Results.vue'
import AddParagraph from './AddParagraph.vue'
import Editor from './Editor.vue'

export default {
  name: 'Paragraph',
  components: {
    'Editor': Editor,
    'Results': Results,
    'AddParagraph': AddParagraph
  },
  props: ['index', 'paragraph', 'noteId', 'setActive'],
  data () {
    return {
      isMdEditorVisible: false
    }
  },
  computed: {
    isHTML: function () {
      const { id } = this.$props.paragraph
      const paragraph = this.$store.getters.getParagraphById(id, this.$props.noteId)

      if (paragraph.result && paragraph.result.type.toLowerCase() === 'html') {
        return true
      }

      return false
    },
    hasTitle: function () {
      if (!this.isHTML) {
        return false
      }

      const { title } = this.$props.paragraph

      if (title === '') {
        return false
      }

      return true
    },
    hasResults: function () {
      if (this.$props.paragraph.results) {
        return true
      }

      return false
    },
    getResults: function () {
      return this.$props.paragraph.results.msg
    }
  },
  methods: {

  }
}
</script>

<style lang="scss" scoped>
.paragraph-main {
  padding-top: 70px;
  margin-top: -70px;

  &.active .paragraph {
    border-left-color: #3290ff;
  }

  .paragraph {
    padding: 0 10px;
    border-left: 5px solid transparent;
  }
}
</style>
