SHELL     := /usr/bin/env bash
MODEL_DIR := model

MODELS ?= $(shell find $(MODEL_DIR) -type f -name '*.json' | sort)

clean:
	lein clean
	rm -rf $(MODEL_DIR)

full-clean: clean
	rm -rf vendor model

$(MODEL_DIR): vendor/google-api-go-client
	@./script/copy-models $< $@

vendor/google-api-go-client:
	git clone https://github.com/google/google-api-go-client $@
