$benchnum  = '458';
$benchname = 'sjeng';
$exename   = 'sjeng';
$benchlang = 'C';
@base_exe  = ($exename);


@sources = qw( 
              attacks.c book.c crazy.c draw.c ecache.c epd.c 
              eval.c leval.c moves.c neval.c partner.c proof.c 
              rcfile.c search.c see.c seval.c sjeng.c ttable.c 
              utils.c
                        );

sub invoke {
    my ($me) = @_;
    my $name;
    my @rc;

    my $exe = $me->exe_file;
    for ($me->input_files_base) {
	if (($name) = m/(.*).txt$/) {
	    push (@rc, { 'command' => $exe, 
			 'args'    => [ "$name.txt" ],
			 'output'  => "$name.out",
			 'error'   => "$name.err",
			});
	}
    }
    return @rc;
}

1;

