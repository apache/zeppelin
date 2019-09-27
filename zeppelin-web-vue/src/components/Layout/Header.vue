<template>
  <div id="header">
    <div style="display: flex">
      <router-link
        to="/"
        class="logo pt-3 pl-2"
      >
        <img alt="Zeppelin logo" src="@/assets/zepLogo.png">
      </router-link>

      <div>
        <h5 class="pt-2 mb-1">
          Zeppelin Notebook
        </h5>
        <TopMenu />
      </div>
    </div>
  </div>
</template>

<script>
import TopMenu from './TopMenu.vue'

export default {
  name: 'Header',
  components: { TopMenu }
}
</script>

<style lang="scss">
#header {
  height: 67px;
  margin: 0;
  padding: 0;

  .logo {
    display: block;
    width: 60px;

    img {
      width: 40px;
    }
  }

  h5 {
    padding-left: 12px;
  }
}
</style>
