#ifndef __traceharbor_resource_canary_memory_util_excludes_h__
#define __traceharbor_resource_canary_memory_util_excludes_h__

#include "traceharbor_hprof_analyzer.h"

using namespace traceharbor::hprof;

bool exclude_default_references(HprofAnalyzer &analyzer);

#endif