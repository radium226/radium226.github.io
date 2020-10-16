#!/usr/bin/env make

SHELL = /usr/bin/env
.SHELLFLAGS = bash -eu -c

.ONESHELL:

include make/git.mk make/articles.mk make/favicon.mk

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
	#make git-configure GIT_FOLDER="repos/postgresql-changes"

.PHONY: generate
## Generate the website
generate: favicon articles clone-public clone-theme
	# Generating static files
	hugo \
		--cleanDestinationDir \
		--destination "public"

.PHONY: deploy
## Deploy the website
deploy: generate 
	git -C "public" add --all
	git -C "public" commit -m "$(shell git log -1 --pretty="%B")"
	git -C "public" push -u "origin" "public"

.PHONY: clean
## Clean all the stuff
clean: 
	rm -Rf "public"
	rm -Rf "themes/devise"
	rm -Rf "static/favicon.ico"
	rm -Rf "posts/posgresql-changes.md"
	rm -Rf "repos"

.PHONY: serve
serve: generate
	hugo server

.PHONY: help
## Display available rules
help:
	@echo "$$(tput bold)Available rules:$$(tput sgr0)";echo;sed -ne"/^## /{h;s/.*//;:d" -e"H;n;s/^## //;td" -e"s/:.*//;G;s/\\n## /---/;s/\\n/ /g;p;}" ${MAKEFILE_LIST}|LC_ALL='C' sort -f|awk -F --- -v n=$$(tput cols) -v i=19 -v a="$$(tput setaf 6)" -v z="$$(tput sgr0)" '{printf"%s%*s%s ",a,-i,$$1,z;m=split($$2,w," ");l=n-i;for(j=1;j<=m;j++){l-=length(w[j])+1;if(l<= 0){l=n-i-length(w[j])-1;printf"\n%*s ",-i," ";}printf"%s ",w[j];}printf"\n";}'|more $(shell test $(shell uname) == Darwin && echo '-Xr')