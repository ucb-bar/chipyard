$benchnum  = '470'; 
$benchname = 'lbm';
$exename   = 'lbm';
$benchlang = 'C';
@base_exe  = ($exename);

$abstol =  0.0000001;

@sources = qw( lbm.c main.c );

$need_math = 'yes';

sub invoke {
    my ($me) = @_;
    my $name = $me->name;
    open ARGUMENTS, "<$name.in" ;
    my $arguments;
    chomp($arguments = <ARGUMENTS>);
    close ARGUMENTS;

    return ({ 'command' => $me->exe_file, 
		 'args'    => [ "$arguments" ], 
		 'error'   => "$name.err",
		 'output'  => "$name.out",
		});
}

1;
