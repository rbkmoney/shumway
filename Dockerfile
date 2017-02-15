# @dockerfile.Template@

# base java service image
FROM dr.rbkmoney.com/rbkmoney/service-java:@dockerfile.base.service.tag@
MAINTAINER Vladimir Pankrashkin <v.pankrashkin@rbkmoney.com>, Pavel Popov <p.popov@rbkmoney.com>

COPY @artifactId@-@version@.jar /opt/@artifactId@/@artifactId@.jar

CMD ["java", "-Xmx512m", "-jar","/opt/@artifactId@/@artifactId@.jar"]

EXPOSE @server.port@

LABEL com.rbkmoney.@artifactId@.parent=service-java \
    com.rbkmoney.@artifactId@.parent_tag=@dockerfile.base.service.tag@ \
    com.rbkmoney.@artifactId@.build_img=build \
    com.rbkmoney.@artifactId@.build_img_tag=@dockerfile.build.container.tag@ \
    com.rbkmoney.@artifactId@.commit_id=@git.revision@ \
    com.rbkmoney.@artifactId@.commit_number=@git.commitsCount@ \
    com.rbkmoney.@artifactId@.branch=@git.branch@
WORKDIR /opt/@artifactId@
