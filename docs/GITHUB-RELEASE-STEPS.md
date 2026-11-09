# GitHub 发布步骤记录

仓库名: josm-block-plugins
作者:   AoiLemon
链接:   https://github.com/AoiLemon/josm-block-plugins (创建后)

## 步骤

1. 创建仓库 (在 GitHub 网页上完成)
2. 初始化本地 git 仓库并推送
3. 创建 v1.0.0 release tag
4. 上传 jar 附件

## 本地 Git 初始化命令

在项目目录执行:

```
cd C:\Users\AoiLemon\Desktop\Workplace\josm-blockfill
git init
git add README.md CHANGELOG.md LICENSE .gitignore plugin.cfg build.ps1 build-layout.ps1 src test resources docs examples
git commit -m "feat: initial 1.0.0 release"
git remote add origin https://github.com/AoiLemon/josm-block-plugins.git
git push -u origin main
git tag v1.0.0
git push origin v1.0.0
```

## Release 说明 (用于 GitHub Releases 页面)

标题: v1.0.0 — BlockFill & BlockLayout 1.0.0

内容: 见 CHANGELOG.md 1.0.0 节
