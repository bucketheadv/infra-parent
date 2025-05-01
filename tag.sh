#!/bin/bash

# 检查 pom.xml 是否存在
if [ ! -f "pom.xml" ]; then
  echo "❌ pom.xml not found in current directory"
  exit 1
fi

# 使用 sed 提取 <project> 中的 <version>，排除 <parent> 中的内容
VERSION=$(sed -n '/<project/,/<\/project>/p' pom.xml \
  | sed '/<parent/,/<\/parent>/d' \
  | grep -m 1 '<version>' \
  | sed -E 's/.*<version>([^<]+)<\/version>.*/\1/')

if [ -z "$VERSION" ]; then
  echo "❌ Failed to extract version from pom.xml"
  exit 1
fi

# 生成 tag 名称
TAG="$VERSION"
echo "📦 Found version: $VERSION"
echo "🏷️  Creating Git tag: $TAG"

# 创建 Git tag
git tag "$TAG"

# 可选：推送到远程仓库
# git push origin "$TAG"

echo "✅ Tag created: $TAG"
