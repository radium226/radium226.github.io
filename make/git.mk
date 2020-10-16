GIT_FOLDER := .

.PHONY: git-configure
## Configure git
git-configure:
	git -C "$(GIT_FOLDER)" config "pull.rebase" "true"
	git -C "$(GIT_FOLDER)" config "user.name" "radium226"
	git -C "$(GIT_FOLDER)" remote set-url "origin" "$$( git -C "$(GIT_FOLDER)" remote get-url "origin" | sed -r -e 's,(-perso)$$,,p' -e 's,(github\.com),\1-perso,g' )"