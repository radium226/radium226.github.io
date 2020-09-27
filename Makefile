#!/usr/bin/env make

SHELL = /usr/bin/env
.SHELLFLAGS = bash -eu -c

.ONESHELL:

include make/articles.mk make/favicon.mk

# Public 
.PHONY: clone-public
clone-public: public/.git/config

public/.git/config:
	mkdir -p "public"
	git clone \
		--single-branch \
		--branch "public" \
		$(shell git config --get "remote.origin.url") \
		"public"

# Theme
.PHONY: clone-theme
clone-theme: themes/devise/.git/config

themes/devise/.git/config:
	mkdir -p "themes/devise"
	git clone \
		--single-branch \
		--branch "master" \
		"https://github.com/austingebauer/devise" \
		"themes/devise"

.PHONY: generate
generate: favicon articles clone-public clone-theme
	# Generating static files
	hugo \
		--cleanDestinationDir \
		--destination "public"

.PHONY: deploy
deploy: generate
	git -C "public" add --all
	git -C "public" commit -m "$(shell git log -1 --pretty="%B")"
	git -C "public" push -u "origin" "public"

.PHONY: clean
clean:
	rm -Rf "public"
	rm -Rf "themes/devise"
	rm -Rf "static/favicon.ico"
	rm -Rf "posts/posgresql-changes.md"
	rm -Rf "repos"

.PHONY: serve
serve:
	hugo server