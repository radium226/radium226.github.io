.PHONY: postgresql-changes
postgresql-changes: content/posts/postgresql-changes.md

content/posts/postgresql-changes.md: repos/postgresql-changes/.git/config
	git -C "repos/postgresql-changes" pull
	cp "repos/postgresql-changes/article.md" "content/posts/postgresql-changes.md"

repos/postgresql-changes/.git/config:
	mkdir -p "repos/postgresql-changes"
	git clone \
		"git@github.com:radium226/postgresql-changes-scala.git" \
		"repos/postgresql-changes"