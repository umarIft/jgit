#!/bin/sh
# Copyright (C) 2009, Google Inc.
# and other copyright owners as documented in the project's IP log.
#
# This program and the accompanying materials are made available
# under the terms of the Eclipse Distribution License v1.0 which
# accompanies this distribution, is reproduced below, and is
# available at http://www.eclipse.org/org/documents/edl-v10.php
#
# All rights reserved.
#
# Redistribution and use in source and binary forms, with or
# without modification, are permitted provided that the following
# conditions are met:
#
# - Redistributions of source code must retain the above copyright
#   notice, this list of conditions and the following disclaimer.
#
# - Redistributions in binary form must reproduce the above
#   copyright notice, this list of conditions and the following
#   disclaimer in the documentation and/or other materials provided
#   with the distribution.
#
# - Neither the name of the Eclipse Foundation, Inc. nor the
#   names of its contributors may be used to endorse or promote
#   products derived from this software without specific prior
#   written permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
# CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
# INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
# OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
# ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
# CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
# SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
# NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
# LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
# CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
# STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
# ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
# ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.


# Update all pom.xml and MANIFEST.MF with new build number
#
# TODO(spearce) This should be converted to some sort of
# Java based Maven plugin so its fully portable.
#

case "$1" in
--snapshot=*)
	V=$(echo "$1" | perl -pe 's/^--snapshot=//')
	if [ -z "$V" ]
	then
		echo >&2 "usage: $0 --snapshot=0.n.0"
		exit 1
	fi
	case "$V" in
	*-SNAPSHOT) : ;;
	*) V=$V-SNAPSHOT ;;
	esac
	;;

--release)
	V=$(git describe HEAD) || exit
	;;

*)
	echo >&2 "usage: $0 {--snapshot=0.n.0 | --release}"
	exit 1
esac

case "$V" in
v*) V=$(echo "$V" | perl -pe s/^v//) ;;
esac

case "$V" in
*-SNAPSHOT)
	POM_V=$V
	MF_V=$(echo "$V" | perl -pe 's/-SNAPSHOT$/.qualifier/')
	;;
*-[1-9]*-g[0-9a-f]*)
	POM_V=$(echo "$V" | perl -pe 's/-(\d+-g.*)$/.$1/')
	MF_V=$POM_V
	;;
*)
	POM_V=$V
	MF_V=$V
	;;
esac

perl -pi -e '
	s/^(Bundle-Version:).*/$1 '"$MF_V"'/
	' $(git ls-files | grep META-INF/MANIFEST.MF)

perl -pi -e '
	if ($ARGV ne $old_argv) {
		$seen_version = 0;
		$old_argv = $ARGV;
	}
	if (!$seen_version) {
		$seen_version = 1 if
		s{(<version>).*(</version>)}{${1}'"$POM_V"'${2}};
	}
	' $(git ls-files | grep pom.xml)

git diff
