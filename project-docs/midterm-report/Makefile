paper.pdf: *.tex 
	pdflatex  paper
	bibtex paper
	pdflatex  paper
#	dvips -P download35 -P pdf -t letter paper.dvi -o $@
clean:
	rm -f *.log *.aux *.dvi *.blg *.bbl *.pdf *.ps
