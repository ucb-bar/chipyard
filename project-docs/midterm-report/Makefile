# The system TeX Live (/usr/bin, 2017) lacks totpages.sty and environ.sty,
# which acmart needs. Use the local TeX Live 2026 install when it exists.
TEXBIN ?= /misc/scratch/$(USER)/texlive/2026/bin/x86_64-linux
ifneq ($(wildcard $(TEXBIN)/pdflatex),)
export PATH := $(TEXBIN):$(PATH)
endif

# nonstopmode: fail on errors instead of stopping at an "Enter file name:" prompt.
LATEX = pdflatex -interaction=nonstopmode -halt-on-error

paper.pdf: *.tex ref.bib
	$(LATEX) paper
	bibtex paper
	$(LATEX) paper
	$(LATEX) paper
#	dvips -P download35 -P pdf -t letter paper.dvi -o $@
clean:
	rm -f *.log *.aux *.dvi *.blg *.bbl *.pdf *.ps
