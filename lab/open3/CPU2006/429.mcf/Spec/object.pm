$benchnum  = '429';
$benchname = 'mcf';
$exename   = 'mcf';
$benchlang = 'C';
@base_exe  = ($exename);

@sources = qw( mcf.c mcfutil.c readmin.c implicit.c pstart.c output.c
	    treeup.c pbla.c pflowup.c psimplex.c pbeampp.c );

$need_math='yes';
$bench_cflags='-DWANT_STDC_PROTO';

sub invoke {
    my ($me) = @_;
    my $name;
    my @rc;

    my $exe = $me->exe_file;
    for ($me->input_files_base) {
	if (($name) = m/(.*).in$/) {
	    push (@rc, { 'command' => $exe, 
			 'args'    => [ $_ ], 
			 'output'  => "$name.out",
			 'error'   => "$name.err",
			});
	}
    }
    @rc;
}

1;
