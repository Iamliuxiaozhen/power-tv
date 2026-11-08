# 主题包（theme packs）

power-tv 的主题包是**纯数据**：一个 `theme.json` 加上可选的图片素材。它不包含 dex、so、脚本，也不会被加载进进程。App 读取 JSON 后把颜色、图表强调色、字体、圆角、背景图和可选的仪表盘布局应用到现有界面。

## 文件格式

支持三种导入形式：

| 形式 | 用途 |
| --- | --- |
| `.ptheme` | 推荐。ZIP 容器，可以带背景图。 |
| `.zip` | 同 `.ptheme`，扩展名不同。 |
| `.json` | 只有配色/配置，没有图片时最方便分享。 |

`.ptheme` 的内部结构：

```text
midnight-forest.ptheme   (zip)
├── theme.json           # 必需，UTF-8
├── preview.webp         # 可选，仅用于作者自己的素材管理，App 会忽略
└── assets/
    └── backdrop.webp    # 可选，背景图
```

`theme.json` 必须位于压缩包根目录，不能放在子目录里。

## 最小示例

```json
{
  "schemaVersion": 1,
  "id": "user.midnight-forest",
  "name": {
    "en": "Midnight Forest",
    "zh-CN": "午夜森林"
  },
  "author": "you",
  "version": "1.0.0",
  "schemes": {
    "light": {
      "primary": "#2E6B4F",
      "surface": "#F6FBF5"
    },
    "dark": {
      "primary": "#8BD5B0",
      "surface": "#0E1512"
    }
  }
}
```

只需要 `primary` 和 `surface` 就能生成完整主题，其余 27 个角色会由 App 派生。想精确控制时再补全即可。

## 字段说明

### 顶层

| 字段 | 必需 | 说明 |
| --- | --- | --- |
| `schemaVersion` | 是 | 当前固定为 `1`。高于 App 支持的版本会被拒绝。 |
| `id` | 是 | 3–64 位，只允许字母、数字、`.`、`_`、`-`，不能包含 `..`。内置主题以 `builtin.` 开头。 |
| `name` | 是 | 字符串，或 `{ "en": "...", "zh-CN": "..." }` 这样的本地化对象（最多 8 个）。 |
| `description` | 否 | 同 `name`，最多 512 字符。 |
| `author` | 否 | 最多 64 字符。 |
| `version` | 否 | 默认 `1.0.0`，建议使用 `主.次.修订`。 |
| `minAppVersion` | 否 | 例如 `1.4.0`。高于当前 App 版本时预览会提示。 |
| `seed` | 否 | 作者记录的种子色，仅作参考，不影响渲染。 |
| `schemes` | 是 | `light` 必需；`dark` 可省略，省略时自动从浅色方案生成。 |
| `dataViz` | 否 | 图表/负载强调色，按 `light`、`dark` 分别设置。 |
| `typography` | 否 | 系统字体族白名单。 |
| `shape` | 否 | 当前只支持卡片圆角。 |
| `backdrop` | 否 | 背景图与压暗程度。 |
| `layout` | 否 | 可选的仪表盘布局预设。 |

未知字段会被忽略；未知颜色角色会在导入时产生警告。

### 颜色角色

`schemes.light` / `schemes.dark` 使用 Material 3 角色名，和 Material Theme Builder 的导出字段一致：

```text
primary, onPrimary, primaryContainer, onPrimaryContainer
secondary, onSecondary, secondaryContainer, onSecondaryContainer
tertiary, onTertiary, tertiaryContainer, onTertiaryContainer
error, onError, errorContainer, onErrorContainer
background, onBackground
surface, onSurface, surfaceVariant, onSurfaceVariant
outline, outlineVariant
surfaceContainerLowest, surfaceContainerLow, surfaceContainer,
surfaceContainerHigh, surfaceContainerHighest
```

颜色支持 `#RGB`、`#RRGGBB`、`#AARRGGBB`。`on*` 角色建议自己指定；省略时 App 会用黑/白或混合色推导，可能出现不理想的对比度。导入预览会检查关键文字/背景组合，低于 4.5:1 时显示提示，但不会阻止使用。

### 图表强调色

```json
"dataViz": {
  "light": {
    "loadLow": "#1F7A66",
    "loadMedium": "#B26A00",
    "loadHigh": "#BA1A1A",
    "chartGrid": "#1A006A67",
    "chartFill": "#55006A67",
    "liveDot": "#1F7A66"
  },
  "dark": { "...": "..." }
}
```

省略的字段会从配色派生：`loadLow` 取 `primary`，`loadHigh` 取 `error`，`chartGrid` / `chartFill` 取带透明度的 `primary`。`loadMedium` 默认是琥珀色。

### 字体

```json
"typography": { "metricFont": "sans-serif-medium", "monoFont": "monospace" }
```

只支持系统字体族：`sans-serif`、`sans-serif-medium`、`sans-serif-light`、`sans-serif-thin`、`sans-serif-condensed`、`serif`、`monospace`。自定义 ttf 暂不加载（需要额外处理授权和体积）。

### 形状

```json
"shape": { "cardRadiusDp": 24 }
```

范围 0–32。省略时保持 App 默认圆角。

### 背景图

```json
"backdrop": { "image": "assets/backdrop.webp", "dim": 0.35 }
```

- 路径必须在 `assets/` 下，扩展名 `png`、`jpg`、`jpeg`、`webp`。
- `dim` 范围 0–0.8，越大背景越暗。
- 解码时限制在 400 万像素以内，避免大图占用内存。
- 图片缺失时该主题仍可使用，只是不显示背景。

### 布局预设

```json
"layout": {
  "order": ["hero", "cpu", "memory", "thermal", "gpu", "battery", "device"],
  "visible": ["hero", "cpu", "memory", "thermal"]
}
```

模块 key 与设置页一致：`hero`、`cpu`、`gpu`、`thermal`、`memory`、`battery`、`device`。

导入/应用时**默认不会**动用户的布局；预览对话框里有一个“同时应用仪表盘布局”的复选框，只有勾选才会覆盖模块顺序和显隐。`visible` 为空时会被忽略，避免把仪表盘全部隐藏。

## 安全限制

主题包是不可信输入，App 会：

- 拒绝 dex/so/脚本；只读取 `theme.json` 和 `assets/` 下的图片。
- 拒绝绝对路径、`..`、空路径段；解压后再次检查真实路径。
- 限制 ZIP 条目数（64）、解压总大小（8 MiB）、`theme.json`（256 KiB）、单张图片（4 MiB）。
- 逐字节计数，不信任 ZIP 头里声明的大小，防止压缩炸弹。
- 拒绝加密/重复 `theme.json`；未知文件跳过。
- 不联网，不需要存储权限，导入通过系统文件选择器（SAF）。

## 作者可以怎么做

### 方式一：App 内从颜色创建

1. 设置 → 主题包 → 新建主题。
2. 输入名称、作者，点主色选择颜色（HSV 滑杆或十六进制）。
3. 预览卡片/图表/pill，满意后“保存并应用”。
4. 在主题列表里点导出，把 `.ptheme` 分享给别人。

这条路径由 App 生成完整配色，适合快速做主题；想精确控制角色时用下面两种方式。

### 方式二：复制模板手写

```bash
cp docs/theme-pack/template.theme.json my-theme/theme.json
$EDITOR my-theme/theme.json
cd my-theme
zip -r ../my-theme.ptheme theme.json assets
```

在 VS Code 里给 `theme.json` 加上 `"$schema": "../theme.schema.json"` 可以获得补全和校验。完整示例见 [`docs/theme-pack/examples/midnight-forest/theme.json`](theme-pack/examples/midnight-forest/theme.json)。

### 方式三：Material Theme Builder

1. 在 [Material Theme Builder](https://material-foundation.github.io/material-theme-builder/) 里配色。
2. 导出 JSON，把 `light` / `dark` 两套角色表拷到 `schemes.light` / `schemes.dark`。
3. 补上 `schemaVersion`、`id`、`name` 和可选扩展字段（`dataViz`、`shape`、`layout` 等）。
4. 打包成 `.ptheme` 导入。

字段名对齐 Material Theme Builder，但 App 不会执行它的导出格式；只需要角色表即可。

### 方式四：导出内置主题再改

主题列表里任意内置主题都可以直接导出。它是可用的完整基线，改颜色后再导入即可。

## 导入/导出

- **导入**：设置 → 主题包 → 导入，选择 `.ptheme` / `.zip` / `.json`。也可以从文件管理器/聊天工具用“打开方式”选择 power-tv（取决于文件提供方给出的 MIME 类型）。
- **预览**：导入后先显示真实组件预览、对比度/版本提示；确认后才写入本机。
- **替换**：相同 `id` 的用户主题会提示是否覆盖；内置主题的 `id` 不允许被覆盖。
- **导出**：主题列表每一项都有导出按钮，生成 `.ptheme`（包含 `assets/`）。
- **删除**：只允许删除用户主题；删除正在使用的主题会回到默认内置主题。

## 已知边界

- 不替换 App 图标、桌面图标、系统通知和冷启动 splash；图标包是后续独立功能。
- 不执行任何代码；需要修改布局结构/新组件的主题包做不到。
- 部分 Material 内部样式（如对话框背景、ripple）仍使用默认主题；卡片、文字、图表、pill、进度、AppBar、底部导航和窗口背景会跟随主题包。
- 照片/字体等素材的授权由主题作者负责。
- 主题包只在本机处理；App 没有网络权限。
