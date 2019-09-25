<template>
  <a v-scroll-to="'#paragraph-' + paragraph.id" href="javascript:void(0)" class="paragraph-mini-inner">
    <span v-on:dblclick="showTitleEditor()" v-on:click.stop="" v-if="!showEditor" class="text-title">
      <span v-if="paragraph.title" class="title">{{paragraph.title}}</span>
      <span v-if="!paragraph.title" class="no-title">Untitled Paragraph</span>
    </span>
    <span v-show="showEditor" class="input"><input v-on:blur="setTitle()" v-on:keyup.enter="setTitle()" :value="paragraph.title" ref="addTitle" placeholder="add title"/></span>
    <!-- <span class="more" v-on:click.stop=""><i data-feather="more-vertical"></i></span> -->
  </a>
</template>

<script>
import wsFactory from '@/services/ws-factory.js'

export default {
  name: 'mini-paragraph',
  components: {

  },
  props: ['paragraph', 'notebookId'],
  data () {
    return {
      showEditor: false
    }
  },
  computed: {

  },
  methods: {
    showTitleEditor: function () {
      this.showEditor = true
      this.$nextTick(() => this.$refs.addTitle.focus())
    },
    setTitle: function () {
      let paragraph = this.$props.paragraph
      let { id, text, config, params } = paragraph
      let newTitle = this.$refs.addTitle.value

      paragraph.title = newTitle
      this.showEditor = false

      // save to local store
      this.$store.dispatch('setParagraph', {
        notebookId: this.$props.notebookId,
        paragraph: paragraph
      })

      // save to api
      wsFactory.getConn(this.$props.notebookId).send({
        op: 'COMMIT_PARAGRAPH',
        data: {
          id: id,
          title: newTitle,
          paragraph: text,
          config: config,
          params: params
        }
      })
    }
  }
}
</script>

<style scoped>
  .more {
    float: right;
    position: relative;
    bottom: 2px;
    color: #989898;
    cursor: default;
  }

  .no-title {
    font-style: italic;
    cursor: auto;
  }

  .title {
    cursor: auto;
  }

  .input input {
    outline: 0;
    padding: 0 10px;
  }

  .paragraph-mini-inner {
    text-decoration: none;
    color: inherit;
    cursor: pointer;
    display: inline-block;
    width: 100%;
    padding: 5px 5px;
  }

  .text-title {
    padding: 0 12px;
    display: inline-block;
    margin-top: 2px;
  }
</style>
