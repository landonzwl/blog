<script setup>
import { RouterLink } from 'vue-router'
</script>

<template>
  <div class="showcase-rbc">
    <div class="showcase-back">
      <RouterLink to="/projects">返回精选作品</RouterLink>
    </div>
  <!-- ================= Hero ================= -->
  <header class="hero">
    <div class="container">
      <div class="badge-row">
        <span class="badge gold">国家自然科学基金重大项目</span>
        <span class="badge">轨道交通控制与安全国家重点实验室</span>
        <span class="badge">轨道交通运行控制国家工程研究中心</span>
      </div>
      <h1>高铁 CTCS-3 列控系统<br>RBC 无线闭塞中心仿真平台</h1>
      <p class="subtitle">
        基于京沪高铁方向代表性区段，仿真高铁 <b>350 km/h</b> 运营背后的地面安全控制核心——
        <b>RBC（无线闭塞中心）</b>：接收列车位置报告、计算并下发行车许可（MA），
        与 CTC 调度中心、CBI 计算机联锁、车载 ATP 三端联调，保障列车不追尾、不冒进。
      </p>
      <div class="hero-tags">
        <span class="tag">C# / .NET</span>
        <span class="tag">UDP Socket</span>
        <span class="tag">多线程</span>
        <span class="tag">二进制协议按位解析</span>
        <span class="tag">故障导向安全</span>
        <span class="tag">三端联调</span>
      </div>
    </div>
  </header>

  <!-- ================= 背景 ================= -->
  <section>
    <div class="container">
      <div class="sec-title">项目背景与学术依托</div>
      <div class="sec-sub">国家级科研团队 · 国家重点实验室 · 国家重大项目，三重背书</div>
      <div class="grid grid-3">
        <div class="card">
          <h3><span class="num">1</span>重大项目</h3>
          <p>国家自然科学基金<b>重大项目</b>《高速铁路运行控制与动态调度一体化基础理论与关键技术》（2018–2022），由<b>北京交通大学牵头</b>，联合浙江大学、中南大学、东北大学、中国铁道科学研究院等共同攻关。</p>
        </div>
        <div class="card">
          <h3><span class="num">2</span>顶尖实验室</h3>
          <p>依托<b>轨道交通控制与安全国家重点实验室</b>与<b>轨道交通运行控制国家工程研究中心</b>（北交大）——国内轨道交通列控领域顶尖平台，拥有完整的半实物仿真积淀。</p>
        </div>
        <div class="card">
          <h3><span class="num">3</span>团队实力</h3>
          <p>项目负责人为<b>国家杰青</b>、重大项目负责人；团队获授权发明专利 <b>70+</b> 项，发表学术论文 <b>300+</b> 篇，主攻轨道交通自主运行、智能感知与动态调度。</p>
        </div>
      </div>
    </div>
  </section>

  <!-- ================= 业务闭环 ================= -->
  <section>
    <div class="container">
      <div class="sec-title">核心业务闭环</div>
      <div class="sec-sub">从列车注册到行车许可下发，完整链路三端联调跑通</div>
      <div class="flow">
        <div class="flow-step"><div class="icon">📝</div><div class="name">列车注册</div><div class="desc">接收 CTC 运行计划</div></div>
        <div class="flow-arrow">→</div>
        <div class="flow-step"><div class="icon">📡</div><div class="name">位置报告</div><div class="desc">车载 ATP 周期上报</div></div>
        <div class="flow-arrow">→</div>
        <div class="flow-step"><div class="icon">🧮</div><div class="name">MA 计算</div><div class="desc">安全位置 + 区间定位</div></div>
        <div class="flow-arrow">→</div>
        <div class="flow-step"><div class="icon">🚦</div><div class="name">许可下发</div><div class="desc">消息 3 组包回送</div></div>
        <div class="flow-arrow">→</div>
        <div class="flow-step"><div class="icon">✅</div><div class="name">车载确认</div><div class="desc">闭环验证</div></div>
      </div>
      <div class="quote">
        支持多列车<b>上下行并发追踪</b>，行车许可随进路状态与前车位置<b>动态更新</b>；
        通信丢失场景下<b>自动封锁区间</b>、恢复后自动解封——体现高铁列控"故障导向安全"设计原则。
      </div>
    </div>
  </section>

  <!-- ================= 技术架构 ================= -->
  <section>
    <div class="container">
      <div class="sec-title">技术架构与核心技术</div>
      <div class="sec-sub">协议层 / 通信层 / 业务层 / 数据层清晰分层</div>
      <div class="grid grid-2">
        <div class="card">
          <h3><span class="num">1</span>按位二进制协议解析</h3>
          <p>报文为 0/1 位串，字段按<b>固定位宽</b>顺序排列，游标截取 + 二进制转数值；以"数值 + 位宽"基元封装，支持字段灵活扩展，无需重写解析主干。</p>
        </div>
        <div class="card">
          <h3><span class="num">2</span>安全位置包络计算</h3>
          <p>针对应答器 + 里程计定位的<b>过读 / 欠读误差</b>，按"最不利原则"计算车头与车尾安全位置上下界，作为 MA 计算输入——铁路<b>故障导向安全</b>思维的代码落地。</p>
        </div>
        <div class="card">
          <h3><span class="num">3</span>前缀和 + 二分查找定位</h3>
          <p>线路闭塞区间预计算为<b>里程前缀和</b>，每列车的区间定位 <b>O(log n)</b> 完成，支撑数十个闭塞区间多车并发实时性。</p>
        </div>
        <div class="card">
          <h3><span class="num">4</span>多模式行车许可（MA）</h3>
          <p>综合前方列车、进路开放、区间封锁取最近障碍点，按<b>固定 / 准移动 / 移动闭塞</b>三种模式输出不同精度的许可终点与静态速度曲线。</p>
        </div>
        <div class="card">
          <h3><span class="num">5</span>多线程通信架构</h3>
          <p>多路<b>UDP 后台线程</b>阻塞接收（CTC / CBI / 车载），锁保护共享列车字典；界面线程周期刷新在线列车状态。</p>
        </div>
        <div class="card">
          <h3><span class="num">6</span>超时降级与自动恢复</h3>
          <p>阈值化通信超时检测 → 区间自动封锁；通信恢复 → 自动解封。功能安全降级思路，与智驾 MRC 最小风险策略同源。</p>
        </div>
      </div>
    </div>
  </section>

  <!-- ================= 参与贡献 ================= -->
  <section>
    <div class="container">
      <div class="sec-title">个人参与情况</div>
      <div class="sec-sub">项目由团队共同完成，本人作为团队成员参与代码书写与联调验证</div>
      <div class="grid grid-2">
        <div class="card">
          <h3><span class="num">①</span>位置报告解析模块</h3>
          <p>参与车载位置报告<b>按位解析</b>与<b>列车安全位置包络计算</b>模块的代码实现（结合过读/欠读误差、车长、方向），完成模块级联调验证。</p>
        </div>
        <div class="card">
          <h3><span class="num">②</span>闭塞定位与 MA 联调</h3>
          <p>参与<b>前缀和 + 二分查找</b>的闭塞区间定位模块开发，协同完成三种闭塞模式下行车许可 MA 计算逻辑的实现与验证。</p>
        </div>
        <div class="card">
          <h3><span class="num">③</span>许可组包与三端联调</h3>
          <p>参与<b>行车许可组包下发</b>模块实现，负责 RBC 与 CBI、CTC、车载 ATP 节点的 UDP 通信收发与三端联调。</p>
        </div>
        <div class="card">
          <h3><span class="num">④</span>超时检测与封锁防护</h3>
          <p>参与<b>通信超时检测与区间封锁防护</b>模块的开发与联调，验证通信丢失场景下的自动降级行为。</p>
        </div>
      </div>
    </div>
  </section>

  <!-- ================= 含金量 ================= -->
  <section>
    <div class="container">
      <div class="sec-title">项目含金量速览</div>
      <div class="sec-sub">六个维度看清这个作品值多少</div>
      <div class="table-wrap">
        <table>
          <tr><th>维度</th><th>含金量</th></tr>
          <tr><td>学术背书</td><td>国家自然科学基金<b>重大项目</b> + 国家重点实验室 + 国家工程研究中心</td></tr>
          <tr><td>团队实力</td><td>项目负责人为<b>国家杰青</b>，发明专利 70+、论文 300+</td></tr>
          <tr><td>业务领域</td><td>高铁 CTCS-3 列控——国内高铁 <b>350 km/h</b> 运营的核心安全系统</td></tr>
          <tr><td>技术含量</td><td>二进制协议按位解析、安全位置包络、多闭塞模式、MA 计算、超时降级</td></tr>
          <tr><td>工程规模</td><td>多线程后台服务、多车并发、三方 UDP 通信、数十个闭塞区间</td></tr>
          <tr><td>可迁移性</td><td>协议解析 / 多线程 / 网络通信 / 安全思维 / 数据结构，可迁移至智能驾驶、车联网、工业控制等方向</td></tr>
        </table>
      </div>
    </div>
  </section>

  <!-- ================= 岗位映射 ================= -->
  <section>
    <div class="container">
      <div class="sec-title">技术能力迁移映射</div>
      <div class="sec-sub">定位 → 安全决策 → 控制执行 → 故障导向安全，四模块通用</div>
      <div class="map-item">
        <div class="from">车载周期上报位置报告（UDP）</div><div class="arrow">➜</div>
        <div class="to">CAN / V2X 周期报文解析（车联网）</div>
      </div>
      <div class="map-item">
        <div class="from">应答器 + 过读/欠读误差 → 位置包络</div><div class="arrow">➜</div>
        <div class="to">多传感器融合 → 带置信区间的位姿估计（智能驾驶）</div>
      </div>
      <div class="map-item">
        <div class="from">RBC 计算行车许可 MA</div><div class="arrow">➜</div>
        <div class="to">规控层输出安全行驶边界（自动驾驶）</div>
      </div>
      <div class="map-item">
        <div class="from">通信超时 → 区间封锁降级</div><div class="arrow">➜</div>
        <div class="to">功能安全降级 → MRC 最小风险策略</div>
      </div>
      <div class="map-item">
        <div class="from">按位宽顺序解析二进制协议</div><div class="arrow">➜</div>
        <div class="to">自定义网络协议 / 报文帧解析（通用能力）</div>
      </div>
      <div class="map-item">
        <div class="from">多线程 + 锁保护共享字典</div><div class="arrow">➜</div>
        <div class="to">线程池 + 并发容器 + 同步机制（并发编程通用能力）</div>
      </div>
    </div>
  </section>

  <!-- ================= Footer ================= -->
  <footer>
    <div class="container">
      <p>高铁 CTCS-3 列控 RBC 仿真平台 · 精选作品展示页</p>
      <p>项目来源：国家自然科学基金重大项目 · 轨道交通控制与安全国家重点实验室</p>
      <span class="warn">⚠ 本页面仅展示项目背景与技术能力概述，不含任何源码、协议字段细节与内部实现</span>
    </div>
  </footer>
  </div>
</template>

<style>

.showcase-rbc .showcase-back {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  padding: 12px 24px;
  background: rgba(11, 16, 32, 0.92);
  border-bottom: 1px solid rgba(255, 255, 255, 0.10);
  backdrop-filter: blur(8px);
}
.showcase-rbc .showcase-back a {
  color: #bcd3ff;
  text-decoration: none;
  font-size: 14px;
}
.showcase-rbc .showcase-back a:hover { color: #fff; }
.showcase-rbc {

    --bg: #0b1020;
    --bg2: #101736;
    --card: rgba(255,255,255,0.04);
    --border: rgba(255,255,255,0.10);
    --text: #e8ecf6;
    --muted: #9aa5c0;
    --accent: #4f8cff;
    --accent2: #22d3a7;
    --gold: #f5c26b;
    --radius: 16px;
  

    font-family: "Microsoft YaHei", "PingFang SC", -apple-system, sans-serif;
    background: linear-gradient(180deg, var(--bg) 0%, var(--bg2) 100%);
    color: var(--text);
    line-height: 1.75;
    min-height: 100vh;
  
  scroll-behavior: smooth;
}
.showcase-rbc, .showcase-rbc * { margin: 0; padding: 0; box-sizing: border-box; }
.showcase-rbc .container{ max-width: 1080px; margin: 0 auto; padding: 0 24px; }.showcase-rbc /* ===== Hero ===== */
  .hero{
    padding: 90px 0 70px;
    text-align: center;
    position: relative;
    overflow: hidden;
  }.showcase-rbc .hero::before{
    content:"";
    position:absolute; top:-200px; left:50%; transform:translateX(-50%);
    width:900px; height:500px;
    background: radial-gradient(closest-side, rgba(79,140,255,0.18), transparent);
    pointer-events:none;
  }.showcase-rbc .badge-row{ display:flex; gap:10px; justify-content:center; flex-wrap:wrap; margin-bottom:28px; }.showcase-rbc .badge{
    font-size: 13px; padding: 6px 16px; border-radius: 999px;
    border: 1px solid var(--border); background: var(--card);
    color: var(--muted);
  }.showcase-rbc .badge.gold{ color: var(--gold); border-color: rgba(245,194,107,0.35); }.showcase-rbc h1{
    font-size: clamp(28px, 5vw, 44px);
    font-weight: 800; letter-spacing: 1px;
    background: linear-gradient(90deg, #fff, #a8c6ff);
    -webkit-background-clip: text; background-clip: text;
    -webkit-text-fill-color: transparent;
    margin-bottom: 18px;
  }.showcase-rbc .subtitle{ color: var(--muted); font-size: 17px; max-width: 720px; margin: 0 auto 36px; }.showcase-rbc .subtitle b{ color: var(--text); font-weight:600; }.showcase-rbc .hero-tags{ display:flex; gap:10px; justify-content:center; flex-wrap:wrap; }.showcase-rbc .tag{
    font-size:13px; padding:7px 14px; border-radius:8px;
    background: linear-gradient(135deg, rgba(79,140,255,0.15), rgba(34,211,167,0.10));
    border:1px solid rgba(79,140,255,0.25); color:#bcd3ff;
  }.showcase-rbc /* ===== Section ===== */
  section{ padding: 56px 0; }.showcase-rbc .sec-title{
    display:flex; align-items:center; gap:14px;
    font-size: 26px; font-weight: 700; margin-bottom: 8px;
  }.showcase-rbc .sec-title::before{
    content:""; width:6px; height:26px; border-radius:3px;
    background: linear-gradient(180deg, var(--accent), var(--accent2));
  }.showcase-rbc .sec-sub{ color:var(--muted); font-size:14px; margin-bottom:32px; padding-left:20px; }.showcase-rbc /* ===== Cards ===== */
  .grid{ display:grid; gap:20px; }.showcase-rbc .grid-2{ grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); }.showcase-rbc .grid-3{ grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); }.showcase-rbc .card{
    background: var(--card); border:1px solid var(--border);
    border-radius: var(--radius); padding: 26px 28px;
    transition: transform .25s, border-color .25s;
  }.showcase-rbc .card:hover{ transform: translateY(-4px); border-color: rgba(79,140,255,0.45); }.showcase-rbc .card h3{ font-size:17px; margin-bottom:12px; display:flex; align-items:center; gap:10px; }.showcase-rbc .card h3 .num{
    font-size:12px; color:var(--accent); border:1px solid rgba(79,140,255,0.4);
    width:22px; height:22px; border-radius:6px;
    display:inline-flex; align-items:center; justify-content:center; flex:none;
  }.showcase-rbc .card p, .showcase-rbc .card li{ color: var(--muted); font-size:14px; }.showcase-rbc .card b{ color: var(--text); font-weight:600; }.showcase-rbc /* ===== Table ===== */
  table{ width:100%; border-collapse:collapse; font-size:14px; }.showcase-rbc th, .showcase-rbc td{ text-align:left; padding:13px 16px; border-bottom:1px solid var(--border); }.showcase-rbc th{ color:var(--text); font-weight:600; white-space:nowrap; }.showcase-rbc td{ color:var(--muted); }.showcase-rbc tr:hover td{ background: rgba(79,140,255,0.05); }.showcase-rbc .table-wrap{
    background: var(--card); border:1px solid var(--border);
    border-radius: var(--radius); overflow:hidden; overflow-x:auto;
  }.showcase-rbc /* ===== Timeline (flow) ===== */
  .flow{ display:flex; flex-wrap:wrap; gap:12px; align-items:stretch; justify-content:center; }.showcase-rbc .flow-step{
    flex:1; min-width:150px; text-align:center;
    background: var(--card); border:1px solid var(--border);
    border-radius: 12px; padding:18px 12px; position:relative;
  }.showcase-rbc .flow-step .icon{ font-size:24px; margin-bottom:8px; }.showcase-rbc .flow-step .name{ font-size:14px; font-weight:600; color:var(--text); }.showcase-rbc .flow-step .desc{ font-size:12px; color:var(--muted); margin-top:4px; }.showcase-rbc .flow-arrow{ align-self:center; color:var(--accent); font-size:20px; }.showcase-rbc /* ===== Mapping ===== */
  .map-item{
    display:grid; grid-template-columns: 1fr 40px 1fr; gap:12px; align-items:center;
    background:var(--card); border:1px solid var(--border);
    border-radius:12px; padding:16px 20px; margin-bottom:12px;
  }.showcase-rbc .map-item .from, .showcase-rbc .map-item .to{ font-size:14px; }.showcase-rbc .map-item .from{ color:#bcd3ff; }.showcase-rbc .map-item .to{ color:#a9e9d5; text-align:right; }.showcase-rbc .map-item .arrow{ text-align:center; color:var(--accent); font-size:18px; }.showcase-rbc /* ===== Quote ===== */
  .quote{
    border-left: 4px solid var(--accent);
    background: linear-gradient(90deg, rgba(79,140,255,0.08), transparent);
    padding: 22px 28px; border-radius: 0 12px 12px 0;
    color: var(--text); font-size:15px; margin-top:28px;
  }.showcase-rbc /* ===== Footer ===== */
  footer{
    margin-top: 60px; padding: 34px 0 44px;
    border-top: 1px solid var(--border);
    text-align:center; color:var(--muted); font-size:13px;
  }.showcase-rbc footer .warn{
    display:inline-block; margin-top:12px;
    background: rgba(245,194,107,0.08); border:1px solid rgba(245,194,107,0.25);
    color: var(--gold); border-radius:8px; padding:8px 18px; font-size:12px;
  }@media (max-width: 640px){
    .map-item { grid-template-columns: 1fr; text-align:center; }
    .map-item .arrow, .map-item .to { text-align:center; }
    .hero { padding: 60px 0 40px; }
  }

</style>
