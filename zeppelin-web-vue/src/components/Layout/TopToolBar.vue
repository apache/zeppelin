<template>
  <div class="top-tool-bar">
    <button
      class="btn btn-light btn-sm"
     >
      <a-icon type="plus" />
      <span>Code</span>
    </button>

    <button
      class="btn btn-light btn-sm"
     >
      <a-icon type="plus" />
      <span>Text</span>
    </button>
  </div>
</template>

<script>
export default {
  name: 'TopToolBar',
  components: {

  }
}
</script>

<style lang="scss" scoped>
.top-tool-bar {
  height: 32px;
  background: #F7F7F7;
  // border-top: 1px solid #DADADA;
  // border-bottom: 1px solid #DADADA;
  padding-left: 8px;
  margin-top: 3px;
  // padding: 0 10px;

  button {
    border-radius: 0;
    vertical-align: middle;
    padding: 4px 8px;

    span {
      padding-left: 3px;
      display: inline-block;
      height: 22px;
      line-height: 22px;
      vertical-align: middle;
    }

    svg {
      width: 14px;
      display: inline-block;
      height: 22px;
      line-height: 22px;
      vertical-align: middle;
    }
  }
}
</style>
