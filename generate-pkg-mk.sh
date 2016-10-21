#!/bin/sh

base_dir=$(dirname $0)

for pkg in $@
do
    pkg_dir="${base_dir}/${pkg}"
    cat <<MAKE
${base_dir}/lib/${pkg}.stamp: \$(call lookup_scala_srcs, ${pkg_dir}) \$(rocketchip_stamp)
	rm -f ${pkg_dir}/lib
	ln -s ${base_dir}/lib ${pkg_dir}/lib
	cd ${pkg_dir} && \$(SBT) package
	cp ${pkg_dir}/target/scala-2.11/*.jar ${base_dir}/lib
	touch \$@
MAKE
done
