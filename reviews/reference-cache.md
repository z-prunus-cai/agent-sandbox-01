# reference-cache 复建步骤

缓存内容不入库，入库的是能完整重建它的步骤。流程规则见 `reviews/rules/reference-cache.md`。

## 复建

```sh
reviews/reference-cache.sh            # 默认建在 <仓库根>/.reference-cache
reviews/reference-cache.sh <目录>     # 或指定位置
```

- 采用仓库内本地目录的形态：目录自带只含 `*` 的 `.gitignore`，根目录的 `.gitignore` 也忽略它。
- 脚本可以重复运行，已存在的条目会跳过。
- 每个条目的 `SOURCE` 记录了在该条目目录内依次执行的全部命令（克隆、稀疏检出、每一步删除），与脚本实际执行的一致。
- 条目建成后设为只读，所在目录也随之上锁。新增条目时脚本只解锁它要写入的那一层。
- 新增条目：先按规则列清单、经审批，再往脚本末尾追加一行 `entry …`，同时更新下表。

## 与规则的偏差（均经仓库所有者批准）

- **稀疏检出**：规则要求整树克隆。`openjdk/jdk`、`microsoft/TypeScript`、`gradle/gradle` 三个超大仓库，以及两个只需要单个子包的 monorepo 条目，改为只检出与审查相关的路径。各条目的 `SOURCE` 里标注了 `sparse`。
- **加大删除力度**：除规则列出的 CI、打包、wrapper、编辑器配置、`.git`、二进制文件和 agent 指令文件外，还删掉了所有测试、快照、基准测试、端到端测试、fixture、playground、测试结果、lockfile、SVG、符号链接、非英文文档翻译，以及仓库自带的 agent `skills` 目录。二进制文件的判定方式是 `grep -I` 认作二进制，而不只看扩展名。
- **MUI 图标**：`packages/mui-icons-material` 里约 2 万个生成的图标组件（88M）被删掉，只留一份 `ICON_NAMES.txt` 名称索引，用来核实图标是否存在。
- **版本不确定时多缓存**：logback 同时缓存了 1.5.38 和 1.6.3。

## 条目

| 条目 | ref | 版本依据 | 形态 |
|---|---|---|---|
| `spring-projects/spring-boot@4.1.1` | `v4.1.1` | `libs.versions.toml` | 整树 |
| `spring-projects/spring-framework@7.0.9` | `v7.0.9` | Spring Boot 4.1.1 BOM | 整树 |
| `FasterXML/jackson-databind@3.1.5` | `jackson-databind-3.1.5` | Boot BOM `jackson-bom` | 整树 |
| `FasterXML/jackson-core@3.1.5` | `jackson-core-3.1.5` | Boot BOM `jackson-bom` | 整树 |
| `qos-ch/logback@1.5.38` | `v_1.5.38` | Boot BOM；实际解析版本不确定 | 整树 |
| `qos-ch/logback@1.6.3` | `v_1.6.3` | libprunus 版本目录；实际解析版本不确定 | 整树 |
| `jakartaee/servlet@6.1.0` | `6.1.0-RELEASE` | Boot BOM | 整树 |
| `flyway/flyway@12.4.0` | `flyway-12.4.0` | Boot BOM | 整树 |
| `jspecify/jspecify@1.0.1` | `v1.0.1` | libprunus 版本目录 | 整树 |
| `libprunus/libprunus-java-core@089136d` | commit | 0.3.0-SNAPSHOT；项目实际用 0.4.0-SNAPSHOT，未公开 | 整树 |
| `gradle/gradle@9.7.1` | `v9.7.1` | `gradle-wrapper.properties` | 稀疏：userguide、dsl 文档和 7 个 API 模块的 `src/main` |
| `node-gradle/gradle-node-plugin@7.1.0` | `7.1.0` | `libs.versions.toml` | 整树 |
| `autonomousapps/dependency-analysis-gradle-plugin@3.19.1` | `v3.19.1` | `settings.gradle.kts` | 整树 |
| `openjdk/jdk@25` | `jdk-25-ga` | libprunus `targetJavaVersion = 25` | 稀疏：`src/java.base/share/classes` |
| `facebook/react@19.3.0` | `v19.3.0` | `package-lock.json` | 整树，删掉 `fixtures`、`compiler` |
| `facebook/react@eslint-plugin-react-hooks-7.1.1` | `eslint-plugin-react-hooks@7.1.1` | `package-lock.json` | 稀疏：插件本体和它内置的 React Compiler 源码 |
| `mui/material-ui@9.4.0` | `v9.4.0` | `package-lock.json` | 整树，删掉翻译、静态资源和生成的图标组件 |
| `emotion-js/emotion@react-11.14.0` | `@emotion/react@11.14.0` | `package-lock.json` | 整树 |
| `emotion-js/emotion@styled-11.14.1` | `@emotion/styled@11.14.1` | `package-lock.json` | 整树 |
| `remix-run/react-router@8.4.0` | `react-router@8.4.0` | `package-lock.json` | 整树 |
| `formatjs/formatjs@react-intl-12.1.1` | `react-intl@12.1.1` | `package-lock.json` | 整树 |
| `vitejs/vite@8.3.0` | `v8.3.0` | `package-lock.json` | 整树 |
| `vitest-dev/vitest@5.0.1` | `v5.0.1` | `package-lock.json` | 整树 |
| `vitejs/vite-plugin-react@plugin-react-6.1.1` | `plugin-react@6.1.1` | `package-lock.json` | 整树 |
| `richardtallent/vite-plugin-singlefile@2.3.3` | commit `fbd6d0d` | 上游没打 2.3.3 tag，用 npm 记录的 gitHead | 整树 |
| `eslint/eslint@10.10.0` | `v10.10.0` | `package-lock.json` | 整树 |
| `eslint/eslint@js-10.0.1` | commit `84fb885` | `@eslint/js` 10.0.1 的 npm gitHead | 稀疏：`packages/js` |
| `typescript-eslint/typescript-eslint@8.70.0` | `v8.70.0` | `package-lock.json` | 整树 |
| `microsoft/TypeScript@6.0.3` | `v6.0.3` | `package-lock.json` | 稀疏：`src/compiler`、`src/lib` |
