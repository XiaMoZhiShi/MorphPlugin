# Morph
基于LibsDisguises的伪装插件

## 使用
1. 访问[Actions](https://github.com/XiaMoZhiShi/MorphPlugin/actions/workflows/build.yml?query=branch%3Amaster+is%3Acompleted)下载最新构建
2. 下载最新版本的[PluginBase](https://github.com/XiaMoZhiShi/PluginBase/releases/latest)和[LibsDisguises](https://www.spigotmc.org/resources/libs-disguises-free.81/)
3. 丢进服务器插件目录中
4. 重启服务器
    * 或者先加载PluginBase再加载此插件（
5. Go!

## TODO List（从上往下按优先级排列）
- [x] 移动JSON配置到插件的配置目录下
    * ~~是的这个插件到现在还在用`/dev/shm/test`当JSON配置存储（~~
      * 现在是插件目录下面的`data.json`了（
- [x] 跟踪由此插件生成的伪装
- [x] 优化帮助信息
    * 根据需求显示而非全部输出到屏幕
- [x] 实现伪装的主动技能
    * 像末影人可以传送，烈焰人投射火球等
    * [ ] 修复末影人伪装可以传送到方块里面的问题
- [x] 实现伪装的被动技能
    * 鱼类在水下呼吸，蝙蝠夜视等
- [x] 使聊天覆盖可配置/和其他聊天插件兼容
- [x] 使插件消息可配置
    * 可能需要更方便的实现，现在直接调用MiniMessage和MorphConfigManager实现的非常麻烦
- [ ] 实现离线玩家存储
    * [x] 实现离线玩家伪装存储
    * [ ] Make it more generic
- [x] 合并`/morph`和`/morphplayer`?
- [x] 允许通过指令为玩家赋予或移除某一伪装?
- [ ] ~~交换请求过期时通知双方？~~
    - 目前没实现是因为还没做离线玩家存储