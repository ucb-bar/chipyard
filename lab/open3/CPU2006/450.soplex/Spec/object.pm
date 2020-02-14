#
# object.pm -- benchmark description file for 000.hello_fp
#
# This file is Perl source code.  You can check the validity of your syntax
# by running
#
#    specperl -cw object.pm
#

# The benchmark numbers are assigned by SPEC.  Please don't change this until
# you're asked to.
$benchnum  = '450';

# You get to choose the benchmark's name. :)  Please don't use spaces or
# punctuation other than _
$benchname = 'soplex';

# This is the base name of the application without any extensions (like .exe
# for systems that require it).  This doesn't necessarily have to be the same
# as the benchmark name, but often is.
$exename   = 'soplex';

# The language that the benchmark is in.  Valid values for this are
# C   - C
# CXX - C++
# F   - FORTRAN
$benchlang = 'CXX';
$need_math = 'yes';
$obiwan = 1;
$these_tolerances_are_as_intended = 'yes indeed';
$reltol = {	'test.out' => 0.0001,
		'train.out' => 0.02,
		'pds-20.mps.out' => 0.02,
		'ref.out' => 0.02,
		'test.mps.info' => 1.0,
                'train.mps.info' => 0.0001,
                'pds-20.mps.info' => 0.0001,
                'ref.mps.info' => 0.0001,
                'pds-50.mps.out' => 0.02,
                'pds-50.mps.info' => 0.0001
          };
#$abstol = 1.0e-5;
$abstol = {     'test.out' => 1.0e-5,
                'train.out' => 1.0e-5,
                'pds-20.mps.out' =>  1.0e-5,
                'ref.out' => 1.0e-5,
                'test.mps.info' => 20,
                'train.mps.info' => 20,
                'pds-20.mps.info' => 20,
                'ref.mps.info' => 20,
                'pds-50.mps.out'=>1.0e-5,
                'pds-50.mps.info'=> 20
          };

# A list of the source files that make up the benchmark.  These files should
# go in the src/ subdirectory.
@sources=qw(
changesoplex.cc
didxset.cc
dsvector.cc
dvector.cc
enter.cc
example.cc
factor.cc
forest.cc
idxset.cc
leave.cc
lpcolset.cc
lprow.cc
lprowset.cc
message.cc
mpsinput.cc
nameset.cc
slufactor.cc
solve.cc
soplex.cc
spxaggregatesm.cc
spxbasis.cc
spxbounds.cc
spxchangebasis.cc
spxdefaultpr.cc
spxdefaultrt.cc
spxdefines.cc
spxdesc.cc
spxdevexpr.cc
spxequilisc.cc
spxfastrt.cc
spxgeneralsm.cc
spxharrisrt.cc
spxhybridpr.cc
spxid.cc
spxio.cc
spxlp.cc
spxlpfread.cc
spxmpsread.cc
spxmpswrite.cc
spxparmultpr.cc
spxquality.cc
spxredundantsm.cc
spxrem1sm.cc
spxscaler.cc
spxshift.cc
spxsolve.cc
spxsolver.cc
spxstarter.cc
spxsteeppr.cc
spxsumst.cc
spxvecs.cc
spxvectorst.cc
spxweightpr.cc
spxweightst.cc
ssvector.cc
svector.cc
svset.cc
timer.cc
unitvector.cc
update.cc
updatevector.cc
vector.cc
vsolve.cc
);

# Please don't change this
@base_exe  = ($exename);

# invoke -- build a list of commands to execute
# This is what is called to build a list of parameter lists.  Execution
# of all of these parameter lists (one execution per list) is timed together
# and is considered one iteration.
sub invoke {
    my ($me) = @_;
    my $name;
    my @rc;

    my $exe = $me->exe_file;
    my $size = $me->size;

  my %maxiters = ( 'test'  => 10000,
                   'train' => 1200,
                   'ref'   => 3500 );
  my %pdsmaxiters = ( 'test'  => 10000,
		   'train' => 5000,
		   'ref'   => 45000 );




  my $maxiter = $maxiters{$size};
  my $pdsmaxiter= $pdsmaxiters{$size};

  foreach my $input_file ($me->input_files_base) {
    if ($input_file !~ /pds/)
    {
    push @rc, { 'command' => $exe,
                'args'    => [ "-m$maxiter", $input_file ],
                'output'  => "$size.out",
                'error'   => "$size.stderr",
                };
    }
   else
   {
   push @rc, { 'command' => $exe,
  		'args'	  => [ "-s1 -e -m$pdsmaxiter", $input_file ],
  		'output'  => "$input_file.out",
  		'error'	  => "$input_file.stderr",
  			};
    }

  }


    return @rc;
}

1;
