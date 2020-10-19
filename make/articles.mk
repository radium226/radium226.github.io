include make/articles/postgresql-changes.mk

.PHONY: articles
articles: postgresql-changes

.PHONY: clean-articles
clean-articles:
	if [[ ! -L "content/articles/posgresql-changes.md" ]]; then
		rm -Rf "content/articles/posgresql-changes.md"
	fi