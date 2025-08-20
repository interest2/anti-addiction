**APP功能**   
对各休闲 APP 用悬浮窗遮住首页的推荐内容，但顶部搜索栏、底部其他菜单不做限制。  
即保留了“搜索引擎”的功能同时，节制了漫无目的的沉迷。
想关闭悬浮窗就得做一道难度适中的算术题。  

**适用范围**  
小红书、知乎、抖音、B 站……，预置支持 6 个APP，还可手动添加其他 APP。  

**apk 安装包**  
见 github 和 gitee 链接：  
https://gitee.com/interest2/anti-addiction/releases  
https://github.com/interest2/anti-addiction/releases  

**技术栈**  
客户端（开源）：Java  
服务端：Java/SpringBoot。  
服务端代码未公开，2 个原因：① 本 APP 的逻辑基本发生在客户端，服务端做的事情很简单，② 服务端代码与其他项目耦合，故暂不适合发出。  
大模型：智谱清言。  

** 上报服务端的信息**  
APP版本号、AndroidID、型号之类（不包括通讯录、定位等等相对敏感的信息）。