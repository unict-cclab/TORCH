@extends('layouts.app')

@section('content')
<div class="container">
    <div class="row justify-content-center">
        <div class="col-md-8">
            <div class="card">
                <div class="card-header">{{ __('Create a new TOSCA scheme') }}</div>

                <div class="card-body">
                    <form method="POST" enctype="multipart/form-data"  action="{{ session('templateIsValid')? route('user.template.store', Auth::id()) : route('user.template.validate', Auth::user() ) }}">
                        @csrf
                        <div class="form-group row">
                            <label for="name" class="col-md-2 col-form-label">{{ __('Name') }}</label>
                            
                            <div class="col-md-10">
                            <input id="name" type="text" class="form-control @error('name') is-invalid @enderror" name="name" value="{{ old('name') }}" required value="{{old('name')}}" autocomplete="name" autofocus>
                                
                                @error('name')
                                <span class="invalid-feedback" role="alert">
                                    <strong>{{ $message }}</strong>
                                </span>
                                @enderror
                            </div>
                        </div>


                            @if(session('templateIsValid'))
                            <div style="height: 300px">
                                    <tosca-graph json_graph="{{session('json_graph')}}"></tosca-graph>            
                            </div>
                            <input type="hidden" name="yaml_template" value="{{session('yaml_template')}}" />
                            <input type="hidden" name="json_graph" value="{{session('json_graph')}}" />
                            <div class="form-group row mb-0">
                                <div class="col-md-6 offset-md-5">
                                    <button type="submit" class="btn btn-primary">
                                        {{ __('Create') }}
                                    </button>
                                </div>
                            </div>

                            @else
                            <div class="input-group mb-3">
                                    <div class="custom-file">
                                        <input type="file" name="yaml_template" class="custom-file-input" id="inputGroupFile" onchange="document.getElementById('input-file-label').innerHTML = this.value.replace(/^.*\\/, '');" required value="{{old('yaml_template')}}"/>
                                        <label class="custom-file-label" for="inputGroupFile" id="input-file-label">Choose file</label>
                                    </div>
                            </div>
                            <div class="form-group row mb-0">
                                <div class="col-md-6 offset-md-5">
                                    <button type="submit" class="btn btn-primary">
                                        {{ __('Validate') }}
                                    </button>
                                </div>
                            </div>
                           
                            @endif

                            @if(session('error'))
                            <br>
                            <div class="alert alert-danger">
                                    <strong>Error!</strong> {{session('error')}}
                                </div>
                            @endif
                            
                        
                    </form>
                </div>
            </div>
        </div>
    </div>
</div>
@endsection
